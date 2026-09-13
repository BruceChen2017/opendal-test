package com.example.opendaldemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Spring Boot entry point.
 *
 * <p>Run with {@code mvn spring-boot:run} and call the API on port 8080, e.g.:
 * <pre>
 * curl -X POST http://localhost:8080/api/sync/write \
 *   -H 'Content-Type: application/json' \
 *   -d '{"path":"demo/a.txt","content":"hello"}'
 * curl 'http://localhost:8080/api/async/read?path=demo/a.txt'
 * </pre>
 */
@SpringBootApplication
@EnableConfigurationProperties(OpenDalProperties.class)
public class OpenDalMinioApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenDalMinioApplication.class, args);
    }
}
