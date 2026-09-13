package com.example.opendaldemo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code opendal.*} settings from {@code application.yml}.
 *
 * <p>Defaults target a local MinIO instance using the standard credentials, so
 * the app starts against a fresh MinIO without any configuration.
 */
@ConfigurationProperties(prefix = "opendal")
public class OpenDalProperties {

    /** S3 endpoint. MinIO's default is http://127.0.0.1:9000. */
    private String endpoint = "http://127.0.0.1:9000";

    /**
     * Signing region. MinIO ignores the value, but the S3 signer requires a
     * non-empty region, so any string works.
     */
    private String region = "us-east-1";

    private String accessKeyId = "minioadmin";

    private String secretAccessKey = "minioadmin";

    /** Bucket that all operations are scoped to. */
    private String bucket = "opendal-demo";

    /** Optional prefix inside the bucket; when set, all paths are relative to it. */
    private String root;

    /**
     * Whether to create the bucket on startup when it is missing.
     *
     * <p>OpenDAL does not create buckets implicitly, so this avoids a confusing
     * first-run failure. Disable it if the app should not have bucket-creation
     * privileges.
     */
    private boolean createBucketIfMissing = true;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    public void setSecretAccessKey(String secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public boolean isCreateBucketIfMissing() {
        return createBucketIfMissing;
    }

    public void setCreateBucketIfMissing(boolean createBucketIfMissing) {
        this.createBucketIfMissing = createBucketIfMissing;
    }
}
