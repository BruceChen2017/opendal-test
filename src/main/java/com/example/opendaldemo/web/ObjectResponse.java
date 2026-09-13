package com.example.opendaldemo.web;

/** Response body returned by read and write endpoints. */
public class ObjectResponse {

    private String path;
    private boolean found;
    private long size;
    private String content;
    private String base64Content;
    private String lastModified;
    private String mode;
    private String message;

    public static ObjectResponse written(String path, long size, String mode) {
        final ObjectResponse r = new ObjectResponse();
        r.path = path;
        r.size = size;
        r.found = true;
        r.mode = mode;
        r.message = "written";
        return r;
    }

    public static ObjectResponse notFound(String path, String mode) {
        final ObjectResponse r = new ObjectResponse();
        r.path = path;
        r.found = false;
        r.mode = mode;
        r.message = "object not found";
        return r;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isFound() {
        return found;
    }

    public void setFound(boolean found) {
        this.found = found;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getBase64Content() {
        return base64Content;
    }

    public void setBase64Content(String base64Content) {
        this.base64Content = base64Content;
    }

    public String getLastModified() {
        return lastModified;
    }

    public void setLastModified(String lastModified) {
        this.lastModified = lastModified;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
