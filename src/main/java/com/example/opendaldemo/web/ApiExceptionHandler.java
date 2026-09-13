package com.example.opendaldemo.web;

import org.apache.opendal.OpenDALException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps OpenDAL failures onto meaningful HTTP status codes.
 *
 * <p>Without this, every backend problem would surface as an opaque 500 and a
 * stack trace. Translating the OpenDAL error code lets clients distinguish
 * "my credentials are wrong" from "the object is missing" from "MinIO is down".
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(OpenDALException.class)
    public ResponseEntity<Map<String, Object>> handleOpenDal(OpenDALException e) {
        final HttpStatus status;
        switch (e.getCode()) {
            case NotFound:
                status = HttpStatus.NOT_FOUND;
                break;
            case PermissionDenied:
                status = HttpStatus.FORBIDDEN;
                break;
            case RateLimited:
                status = HttpStatus.TOO_MANY_REQUESTS;
                break;
            case Unsupported:
                status = HttpStatus.NOT_IMPLEMENTED;
                break;
            case ConfigInvalid:
                status = HttpStatus.INTERNAL_SERVER_ERROR;
                break;
            default:
                status = HttpStatus.BAD_GATEWAY;
                break;
        }
        log.warn("OpenDAL error [{}]: {}", e.getCode(), e.getMessage());
        return ResponseEntity.status(status).body(body(
                status, "OpenDAL error: " + e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        final String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("invalid request");
        return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST, "Validation failed", detail));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST, "Invalid argument", e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleOther(RuntimeException e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", e.getMessage()));
    }

    private static Map<String, Object> body(HttpStatus status, String error, String detail) {
        final Map<String, Object> body = new HashMap<>();
        body.put("status", status.value());
        body.put("error", error);
        body.put("detail", detail);
        return body;
    }
}
