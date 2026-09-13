package com.example.opendaldemo;

import java.util.HashMap;
import java.util.Map;

import org.apache.opendal.Operator;

public class SimpleCRUD {
    

    public static void main(String[] args) {
        Config config = new Config();
        Operator operator = Operator.of("s3", config.toConfigMap());
        // Perform CRUD operations using the operator
        operator.write("example.txt", "Hello, OpenDAL!");
        byte[] content = operator.read("example.txt");
        System.out.println("Read content: " + new String(content, java.nio.charset.StandardCharsets.UTF_8));
        operator.close();
    }

    public static class Config {

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
        static Map<String, String> toConfigMap(Config props) {
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

        Map<String, String> toConfigMap() {
            return toConfigMap(this);
        }

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
}
