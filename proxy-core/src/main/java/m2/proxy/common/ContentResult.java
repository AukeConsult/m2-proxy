package m2.proxy.common;

import com.google.gson.JsonObject;

public class ContentResult {

    private final String contentType;
    private final String status = "200 OK";
    private final String body;

    public String getContentType() {  return contentType; }
    public String getStatus() { return status; }
    public String getBody() { return body; }

    public int length() {
        return body.length();
    }
    public ContentResult (String body) {
        this.contentType="plain/text";
        this.body=body;
    }
    public ContentResult (JsonObject body) {
        this.contentType="application/json";
        this.body=body.toString();
    }
}