package m2.proxy;

import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

public class Forward {

    protected final RawHttp http = new RawHttp();

    public RawHttpRequest makeRequest(String request) {
        return http.parseRequest(request);
    }

    protected RawHttpResponse<?> makeErrorReply(String message) {
        return http.parseResponse("HTTP/1.1 404 NOT OPEN\r\n" +
                "Content-Type: plain/text\r\n" +
                "Content-Length: " + message.length() + "\r\n" +
                "Server: Casa-IO\r\n" +
                "Date: " + RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC)) + "\r\n" +
                "\r\n"+
                message
        );
    }
}
