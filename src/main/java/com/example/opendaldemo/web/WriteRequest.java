package com.example.opendaldemo.web;

import javax.validation.constraints.NotBlank;

/** Request body for write operations. */
public class WriteRequest {

    @NotBlank(message = "path must not be blank")
    private String path;

    /** UTF-8 text content. Either {@code content} or {@code base64Content} is required. */
    private String content;

    /** Binary content, base64-encoded. Takes precedence over {@code content} when set. */
    private String base64Content;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
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
}
