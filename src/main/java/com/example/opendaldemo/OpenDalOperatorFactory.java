package com.example.opendaldemo;

import org.apache.opendal.AsyncOperator;
import org.apache.opendal.OpenDALException;
import org.apache.opendal.Operator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;

/**
 * Owns the two OpenDAL operators and their native resources.
 *
 * <p>Both {@link Operator} and {@link AsyncOperator} wrap a native handle and
 * implement {@code AutoCloseable}. They are <em>not</em> cheap to create, so a
 * single instance of each is created at startup and reused for the lifetime of
 * the application context, then released on shutdown in {@link #close()}.
 */
@Component
public class OpenDalOperatorFactory {

    private static final Logger log = LoggerFactory.getLogger(OpenDalOperatorFactory.class);

    private final OpenDalProperties props;
    private final Operator syncOperator;
    private final AsyncOperator asyncOperator;

    public OpenDalOperatorFactory(OpenDalProperties props) {
        this.props = props;
        final Map<String, String> config = toConfigMap(props);
        log.info("Creating OpenDAL S3 operators for endpoint={} bucket={}", props.getEndpoint(), props.getBucket());
        // Operator.of(...) internally builds an AsyncOperator and wraps it in blocking mode.
        this.syncOperator = Operator.of("s3", config);
        this.asyncOperator = AsyncOperator.of("s3", config);
    }

    /**
     * Translates Spring properties into OpenDAL's S3 config keys.
     *
     * <p>Two entries matter specifically for MinIO:
     * <ul>
     *   <li>{@code enable_virtual_host_style=false} - MinIO requires path-style
     *       addressing. Without this, OpenDAL would request
     *       {@code http://bucket.127.0.0.1:9000}, which cannot resolve.</li>
     *   <li>{@code disable_config_load=true} and {@code disable_ec2_metadata=true} -
     *       stop stray {@code AWS_*} environment variables or an EC2 metadata
     *       lookup from silently overriding the configured credentials.</li>
     * </ul>
     */
    static Map<String, String> toConfigMap(OpenDalProperties props) {
        final Map<String, String> config = new HashMap<>();
        config.put("endpoint", props.getEndpoint());
        config.put("region", props.getRegion());
        config.put("access_key_id", props.getAccessKeyId());
        config.put("secret_access_key", props.getSecretAccessKey());
        config.put("bucket", props.getBucket());
        config.put("enable_virtual_host_style", "false");
        config.put("disable_config_load", "true");
        config.put("disable_ec2_metadata", "true");
        if (props.getRoot() != null && !props.getRoot().trim().isEmpty()) {
            config.put("root", props.getRoot().trim());
        }
        return config;
    }

    public Operator sync() {
        return syncOperator;
    }

    public AsyncOperator async() {
        return asyncOperator;
    }

    public OpenDalProperties properties() {
        return props;
    }

    public String bucket() {
        return props.getBucket();
    }

    /**
     * Verifies connectivity once the context is up, and optionally creates the
     * bucket.
     *
     * <p>Failures are logged rather than fatal so the application still starts and
     * can report a useful error through the API when MinIO is unavailable.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void checkOnStartup() {
        try {
            if (props.isCreateBucketIfMissing()) {
                ensureBucket();
                log.info("OpenDAL is ready; bucket '{}' is available", props.getBucket());
            } else {
                syncOperator.list("/");
                log.info("OpenDAL is ready; bucket '{}' is reachable", props.getBucket());
            }
        } catch (RuntimeException e) {
            log.error("OpenDAL startup check failed for endpoint {}: {}",
                    props.getEndpoint(), e.getMessage());
        }
    }

    /**
     * Creates the bucket if it does not exist.
     *
     * <p>Two S3 details are handled here:
     * <ul>
     *   <li>{@code list("/")} is the authoritative existence check. Probing with
     *       {@code stat(bucket)} is misleading because MinIO answers it without
     *       error even when the bucket is absent.</li>
     *   <li>{@code createDir} requires a path ending in {@code "/"}, otherwise it
     *       fails with {@code NotADirectory}.</li>
     * </ul>
     */
    public synchronized void ensureBucket() {
        try {
            syncOperator.list("/");
            return;
        } catch (OpenDALException e) {
            if (e.getCode() != OpenDALException.Code.NotFound) {
                throw e;
            }
        }
        final String bucket = props.getBucket();
        final String dir = bucket.endsWith("/") ? bucket : bucket + "/";
        syncOperator.createDir(dir);
        log.info("Created bucket '{}'", bucket);
    }

    /** Releases both native handles. OpenDAL requires this to avoid leaking handles. */
    @PreDestroy
    public void close() {
        log.info("Closing OpenDAL operators");
        try {
            asyncOperator.close();
        } catch (RuntimeException e) {
            log.warn("Error closing AsyncOperator", e);
        }
        try {
            syncOperator.close();
        } catch (RuntimeException e) {
            log.warn("Error closing Operator", e);
        }
    }
}
