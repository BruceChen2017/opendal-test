package com.example.opendaldemo.service;

import com.example.opendaldemo.OpenDalOperatorFactory;
import com.example.opendaldemo.web.ObjectResponse;
import com.example.opendaldemo.web.WriteRequest;
import org.apache.opendal.Entry;
import org.apache.opendal.Metadata;
import org.apache.opendal.OpenDALException;
import org.apache.opendal.Operator;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

/**
 * Object storage operations, offered in both blocking and asynchronous flavours.
 *
 * <p>The blocking methods use OpenDAL's {@link Operator}; the async methods use
 * the {@code AsyncOperator} held by the factory and return
 * {@link CompletableFuture}. This lets a caller pick the style per request
 * without changing the underlying configuration.
 */
@Service
public class ObjectStorageService {

    public static final String MODE_SYNC = "sync";
    public static final String MODE_ASYNC = "async";

    private final OpenDalOperatorFactory factory;

    public ObjectStorageService(OpenDalOperatorFactory factory) {
        this.factory = factory;
    }

    // ---------------------------------------------------------------------
    // Synchronous (blocking) operations
    // ---------------------------------------------------------------------

    /** Blocking write. Returns once the object is durably stored. */
    public ObjectResponse writeSync(WriteRequest request) {
        final byte[] payload = payloadOf(request);
        factory.sync().write(request.getPath(), payload);
        return ObjectResponse.written(request.getPath(), payload.length, MODE_SYNC);
    }

    /** Blocking read. Throws {@link ObjectNotFoundException} when the key is absent. */
    public ObjectResponse readSync(String path) {
        if (!existsSync(path)) {
            return ObjectResponse.notFound(path, MODE_SYNC);
        }
        final byte[] data = factory.sync().read(path);
        return toResponse(path, data, factory.sync().stat(path), MODE_SYNC);
    }

    public boolean existsSync(String path) {
        try {
            factory.sync().stat(path);
            return true;
        } catch (OpenDALException e) {
            if (e.getCode() == OpenDALException.Code.NotFound) {
                return false;
            }
            throw e;
        }
    }

    public void deleteSync(String path) {
        factory.sync().delete(path);
    }

    public List<String> listSync(String prefix) {
        final List<Entry> entries = factory.sync().list(prefix == null ? "/" : prefix);
        return entries.stream()
                .map(Entry::getPath)
                .sorted()
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------------
    // Asynchronous (non-blocking) operations
    // ---------------------------------------------------------------------

    /**
     * Asynchronous write.
     *
     * <p>Returns a future that completes when the object has been stored. The
     * calling thread is not blocked, so the servlet container thread is released
     * immediately (see {@code TestController} for how the future is returned to
     * Spring without blocking).
     */
    public CompletableFuture<ObjectResponse> writeAsync(WriteRequest request) {
        final byte[] payload = payloadOf(request);
        return factory.async()
                .write(request.getPath(), payload)
                .thenApply(ignored -> ObjectResponse.written(request.getPath(), payload.length, MODE_ASYNC));
    }

    /**
     * Asynchronous read.
     *
     * <p>Chains stat + read with {@code thenCompose} so no thread blocks between
     * the two dependent operations, then maps to the response DTO.
     */
    public CompletableFuture<ObjectResponse> readAsync(String path) {
        return factory.async()
                .stat(path)
                .thenCompose(meta -> factory.async().read(path)
                        .thenApply(data -> toResponse(path, data, meta, MODE_ASYNC)))
                // A missing object surfaces as NotFound; report it as found=false
                // rather than failing the whole request.
                .exceptionally(throwable -> {
                    final Throwable cause = unwrap(throwable);
                    if (cause instanceof OpenDALException
                            && ((OpenDALException) cause).getCode() == OpenDALException.Code.NotFound) {
                        return ObjectResponse.notFound(path, MODE_ASYNC);
                    }
                    throw new CompletionException(cause);
                });
    }

    public CompletableFuture<Void> deleteAsync(String path) {
        return factory.async().delete(path);
    }

    public CompletableFuture<List<String>> listAsync(String prefix) {
        return factory.async()
                .list(prefix == null ? "/" : prefix)
                .thenApply(entries -> entries.stream()
                        .map(Entry::getPath)
                        .sorted()
                        .collect(Collectors.toList()));
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * Extracts the payload, preferring base64 when present so callers can send
     * binary data. An empty body is treated as an empty object rather than an error.
     */
    private static byte[] payloadOf(WriteRequest request) {
        if (request.getBase64Content() != null) {
            return Base64.getDecoder().decode(request.getBase64Content());
        }
        if (request.getContent() != null) {
            return request.getContent().getBytes(StandardCharsets.UTF_8);
        }
        return new byte[0];
    }

    /**
     * Fills the response DTO.
     *
     * <p>Content is exposed as {@code content} only when it is safe for a JSON
     * string: valid UTF-8 with no control characters other than the common
     * whitespace ones. Anything else is returned as base64 so binary payloads are
     * not mangled by JSON encoding.
     */
    private static ObjectResponse toResponse(String path, byte[] data, Metadata meta, String mode) {
        final ObjectResponse response = new ObjectResponse();
        response.setPath(path);
        response.setFound(true);
        response.setSize(data.length);
        response.setMode(mode);
        response.setMessage("read");
        if (meta != null) {
            response.setLastModified(String.valueOf(meta.getLastModified()));
        }

        if (isTextPayload(data)) {
            response.setContent(new String(data, StandardCharsets.UTF_8));
        } else {
            response.setBase64Content(Base64.getEncoder().encodeToString(data));
        }
        return response;
    }

    /**
     * Decides whether the payload can be safely returned as a JSON string.
     *
     * <p>A valid-UTF-8 check alone is not enough: many binary files consist of
     * bytes that happen to decode into control characters, which would be
     * corrupted or unreadable in JSON. So control characters other than tab,
     * newline and carriage return mark the payload as binary.
     */
    private static boolean isTextPayload(byte[] data) {
        // Round-trip check: decoding and re-encoding must reproduce the bytes,
        // which rules out malformed UTF-8 sequences.
        final String decoded = new String(data, StandardCharsets.UTF_8);
        if (!java.util.Arrays.equals(decoded.getBytes(StandardCharsets.UTF_8), data)) {
            return false;
        }
        for (int i = 0; i < decoded.length(); i++) {
            final char c = decoded.charAt(i);
            if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                return false;
            }
        }
        return true;
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }
}
