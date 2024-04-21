package m2.proxy;

import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

public abstract class LocalSite {

    public static class ContentResult {
        public String contentType = "plain/text";
        public String body;
        public String status="200 OK";
    }
    private RawHttp http = new RawHttp();


    public LocalSite() {}
    public Optional<RawHttpResponse<?>> forward (RawHttpRequest request) {

        final String path = request.getStartLine().getUri().getPath();
        Optional<ContentResult> result = onHandlePath(request.getMethod(), path, request.getBody().toString());
        if(result.isPresent()) {

            String dateString = RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC));
            RawHttpResponse<?> response = http.parseResponse("HTTP/1.1 " + result.get().status + "\r\n" +
                    "Content-Type: " + result.get().contentType + "\r\n" +
                    "Content-Length: " + result.get().body.length() + "\r\n" +
                    "Server: Casa-IO\r\n" +
                    "Date: " + dateString + "\r\n" +
                    "\r\n" +
                    result.get().body);

            return Optional.of(response);

        } else {

            String hello = "Hello from Casa-IO";
            String dateString = RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC));
            RawHttpResponse<?> response = http.parseResponse("HTTP/1.1 200 OK\r\n" +
                    "Content-Type: plain/text\r\n" +
                    "Content-Length: " + hello.length() + "\r\n" +
                    "Server: Casa-IO\r\n" +
                    "Date: " + dateString + "\r\n" +
                    "\r\n" +
                    hello);

            return Optional.of(response);

        }
        // create reply
    }
    abstract Optional<ContentResult> onHandlePath(String verb, String path, String body);

}
