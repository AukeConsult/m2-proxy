package m2.proxy;

import m2.proxy.common.HttpException;
import m2.proxy.common.ProxyStatus;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

public abstract class LocalSite extends Forward {

    public static class ContentResult {
        public String contentType = "plain/text";
        public String body;
        public String status="200 OK";
    }

    public LocalSite() {}
    public Optional<RawHttpResponse<?>> forwardHttp (RawHttpRequest request) throws HttpException {

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
            throw new HttpException(ProxyStatus.NOTFOUND,path);
        }
    }
    abstract Optional<ContentResult> onHandlePath(String verb, String path, String body) throws HttpException;

}
