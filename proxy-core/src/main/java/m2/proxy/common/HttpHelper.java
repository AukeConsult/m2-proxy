package m2.proxy.common;

import com.google.gson.JsonObject;
import rawhttp.core.*;
import rawhttp.core.body.BodyReader;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

public class HttpHelper {

    private final RawHttp rawHttp = new RawHttp();

    public RawHttpRequest parseRequest(String request) {
        return rawHttp.parseRequest(request);
    }

    public Optional<RawHttpResponse<?>> response(ContentResult resultOut) {
        RawHttpResponse<?> response = rawHttp.parseResponse(
                "HTTP/1.1 " +
                        resultOut.getStatus() + "\r\n" +
                        "Content-Type: " + resultOut.getContentType() + "\r\n" +
                        "Content-Length: " + resultOut.length() + "\r\n" +
                        "Server: Casa-IO\r\n" +
                        "Date: " + RFC_1123_DATE_TIME.format( ZonedDateTime.now( ZoneOffset.UTC ) ) + "\r\n" +
                        "\r\n" +
                        resultOut.getBody() );
        return Optional.of( response );
    }



    public Optional<RawHttpResponse<?>> errResponse(ProxyStatus status, String message) {
        return Optional.of(rawHttp.parseResponse( reply(404, status, message)));
    }

    public RawHttpRequest updateRequest(String removePath, String destination, String hostAddress, RawHttpRequest request) {

        RequestLine startLine = request.getStartLine();
        String path = ("##" + startLine.getUri().getPath()).replaceFirst( "##" + removePath, "" )
                +
                (
                        startLine.getUri().getRawQuery() != null ? "?" + startLine.getUri().getRawQuery() : ""
                );

        request = request.withRequestLine(
                rawHttp.getMetadataParser()
                        .parseRequestLine( startLine.getMethod() + " " + path ).withHost( destination )
        ).withHeaders( RawHttpHeaders.newBuilder()
                .with("X-Forwarded-For",hostAddress)
                .build()
        );
        return request;

    }

    public Optional<RawHttpRequest> forward(DirectSite site, RawHttpRequest request)  {

        if(request.getStartLine().getUri().getPath().startsWith( site.getPath() )) {
            return Optional.of(updateRequest( site.getPath(),site.getDestination(), getHostAddress(request), request));
        } else {
            return Optional.empty();
        }
    }

    public Optional<RawHttpRequest> forward(String accessKey, RawHttpRequest request)  {
        if(request.getStartLine().getUri().getPath().startsWith( "/" + accessKey )) {
            return Optional.of(updateRequest( "/" + accessKey,request.getStartLine().getUri().getHost(), getHostAddress(request), request));
        } else {
            return Optional.empty();
        }
    }

    public Optional<String> getAccessPath(RawHttpRequest request) {
        final String[] path = request.getStartLine().getUri().getPath().split("/");
        if(path.length>1) {
            return Optional.of(path[1]);
        } else {
            return Optional.empty();
        }
    }

    public String getHostAddress(RawHttpRequest request) {
        String hostAddress = "";
        try {
            hostAddress = request.getSenderAddress().orElse( InetAddress.getLocalHost() ).getHostAddress();
        } catch (UnknownHostException ignored) {
        }
        return hostAddress;
    }

    public Function<BodyReader, String> decodeBody() {
        return b -> {
            try {
                return b.decodeBodyToString(UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    public String reply(int statusCode, ProxyStatus status) {
        return "HTTP/1.1 " +  statusCode + " " + status.toString() +"\r\n" +
                "Content-Type: plain/text\r\n" +
                "Content-Length: 0\r\n" +
                "Server: Casa-IO\r\n" +
                "Date: " + RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC)) + "\r\n" +
                "\r\n";
    }

    public String reply(int statusCode, ProxyStatus status, String message) {
        return "HTTP/1.1 " +  statusCode + " " + status.toString() +"\r\n" +
                "Content-Type: plain/text\r\n" +
                "Content-Length: " + message.length() + "\r\n" +
                "Server: Casa-IO\r\n" +
                "Date: " + RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC)) + "\r\n" +
                "\r\n"+
                message;
    }

    public String reply(int statusCode, ProxyStatus status, JsonObject jsonRet) {
        String message = jsonRet.toString();
        return "HTTP/1.1 " +  statusCode + " " + status.toString() +"\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: " + message.length() + "\r\n" +
                "Server: Casa-IO\r\n" +
                "Date: " + RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC)) + "\r\n" +
                "\r\n"+
                message;
    }
}
