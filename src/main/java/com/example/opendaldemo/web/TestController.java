package com.example.opendaldemo.web;

import com.example.opendaldemo.OpenDalOperatorFactory;
import com.example.opendaldemo.service.ObjectStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST API for reading and writing objects in MinIO through OpenDAL.
 *
 * <p>Every operation is exposed in two flavours so the difference is visible:
 * <ul>
 *   <li>{@code /api/sync/...} - uses the blocking {@code Operator}. Each call
 *       occupies the request thread until MinIO responds.</li>
 *   <li>{@code /api/async/...} - uses the {@code AsyncOperator} and returns a
 *       {@link CompletableFuture}. Spring MVC completes the request when the
 *       future resolves, freeing the container thread in the meantime.</li>
 * </ul>
 * The two families are interchangeable; choose async when you expect concurrency
 * and want to avoid tying up request threads on network waits.
 */
@RestController
@RequestMapping("/api")
public class TestController {

    private final ObjectStorageService service;
    private final OpenDalOperatorFactory factory;

    public TestController(ObjectStorageService service, OpenDalOperatorFactory factory) {
        this.service = service;
        this.factory = factory;
    }

    // ---------------------------------------------------------------------
    // Metadata
    // ---------------------------------------------------------------------

    /** Shows which MinIO endpoint and bucket the app is configured against. */
    @GetMapping("/info")
    public Map<String, Object> info() {
        final Map<String, Object> info = new HashMap<>();
        info.put("endpoint", factory.properties().getEndpoint());
        info.put("bucket", factory.properties().getBucket());
        info.put("region", factory.properties().getRegion());
        info.put("root", factory.properties().getRoot());
        info.put("modes", new String[] {"sync", "async"});
        return info;
    }

    // ---------------------------------------------------------------------
    // Synchronous endpoints
    // ---------------------------------------------------------------------

    /**
     * Blocking write. Returns 200 with the size written.
     *
     * <pre>
     * curl -X POST http://localhost:8080/api/sync/write \
     *   -H 'Content-Type: application/json' \
     *   -d '{"path":"demo/a.txt","content":"hello sync"}'
     * </pre>
     */
    @PostMapping("/sync/write")
    public ObjectResponse writeSync(@Valid @RequestBody WriteRequest request) {
        return service.writeSync(request);
    }

    /**
     * Blocking read. Returns {@code found=false} when the key does not exist
     * instead of a 404, which keeps the response shape uniform.
     */
    @GetMapping("/sync/read")
    public ObjectResponse readSync(@RequestParam("path") String path) {
        return service.readSync(path);
    }

    @GetMapping("/sync/exists")
    public Map<String, Object> existsSync(@RequestParam("path") String path) {
        final Map<String, Object> result = new HashMap<>();
        result.put("path", path);
        result.put("exists", service.existsSync(path));
        result.put("mode", ObjectStorageService.MODE_SYNC);
        return result;
    }

    @DeleteMapping("/sync/delete")
    public Map<String, Object> deleteSync(@RequestParam("path") String path) {
        service.deleteSync(path);
        final Map<String, Object> result = new HashMap<>();
        result.put("path", path);
        result.put("deleted", true);
        result.put("mode", ObjectStorageService.MODE_SYNC);
        return result;
    }

    @GetMapping("/sync/list")
    public Map<String, Object> listSync(@RequestParam(value = "prefix", required = false) String prefix) {
        final List<String> keys = service.listSync(prefix);
        final Map<String, Object> result = new HashMap<>();
        result.put("prefix", prefix);
        result.put("count", keys.size());
        result.put("keys", keys);
        result.put("mode", ObjectStorageService.MODE_SYNC);
        return result;
    }

    // ---------------------------------------------------------------------
    // Asynchronous endpoints
    // ---------------------------------------------------------------------

    /**
     * Non-blocking write. Spring MVC subscribes to the returned future and writes
     * the response when it completes.
     *
     * <pre>
     * curl -X POST http://localhost:8080/api/async/write \
     *   -H 'Content-Type: application/json' \
     *   -d '{"path":"demo/b.txt","content":"hello async"}'
     * </pre>
     */
    @PostMapping("/async/write")
    public CompletableFuture<ObjectResponse> writeAsync(@Valid @RequestBody WriteRequest request) {
        return service.writeAsync(request);
    }

    /** Non-blocking read. */
    @GetMapping("/async/read")
    public CompletableFuture<ObjectResponse> readAsync(@RequestParam("path") String path) {
        return service.readAsync(path);
    }

    @DeleteMapping("/async/delete")
    public CompletableFuture<Map<String, Object>> deleteAsync(@RequestParam("path") String path) {
        return service.deleteAsync(path)
                .thenApply(ignored -> {
                    final Map<String, Object> result = new HashMap<>();
                    result.put("path", path);
                    result.put("deleted", true);
                    result.put("mode", ObjectStorageService.MODE_ASYNC);
                    return result;
                });
    }

    @GetMapping("/async/list")
    public CompletableFuture<Map<String, Object>> listAsync(
            @RequestParam(value = "prefix", required = false) String prefix) {
        return service.listAsync(prefix)
                .thenApply(keys -> {
                    final Map<String, Object> result = new HashMap<>();
                    result.put("prefix", prefix);
                    result.put("count", keys.size());
                    result.put("keys", keys);
                    result.put("mode", ObjectStorageService.MODE_ASYNC);
                    return result;
                });
    }

    /**
     * Demonstrates genuine concurrency: issues one async read per requested key in
     * parallel and combines them, rather than reading them one after another.
     *
     * <pre>
     * curl 'http://localhost:8080/api/async/read-many?paths=demo/a.txt,demo/b.txt'
     * </pre>
     */
    @GetMapping("/async/read-many")
    public CompletableFuture<Map<String, Object>> readMany(@RequestParam("paths") List<String> paths) {
        final List<CompletableFuture<ObjectResponse>> futures = paths.stream()
                .map(service::readAsync)
                .collect(java.util.stream.Collectors.toList());

        return CompletableFuture
                .allOf(futures.toArray(new CompletableFuture<?>[0]))
                .thenApply(ignored -> {
                    final Map<String, Object> result = new HashMap<>();
                    result.put("count", futures.size());
                    result.put("objects", futures.stream()
                            .map(CompletableFuture::join)
                            .collect(java.util.stream.Collectors.toList()));
                    result.put("mode", ObjectStorageService.MODE_ASYNC);
                    return result;
                });
    }

    /** Health-style endpoint that reports whether MinIO is reachable. */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        final Map<String, Object> result = new HashMap<>();
        try {
            final int count = service.listSync("/").size();
            result.put("minio", "up");
            result.put("objectsAtRoot", count);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            result.put("minio", "down");
            result.put("error", e.getMessage());
            return ResponseEntity.status(503).body(result);
        }
    }
}
