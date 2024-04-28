package m2.proxy.common;

import m2.proxy.client.DirectSite;
import rawhttp.core.*;
import rawhttp.core.body.BodyReader;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;

public class HttpHelper {

    private final RawHttp rawHttp = new RawHttp();

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

    public String getAccessPath(RawHttpRequest request) {
        final String[] path = request.getStartLine().getUri().getPath().split("/");
        if(path.length>1) {
            return path[1];
        } else {
            return "";
        }
    }

    public String getHostAddress(RawHttpRequest request) {
        String hostAddress = "";
        try {
            hostAddress = request.getSenderAddress().orElse( InetAddress.getLocalHost() ).getHostAddress();
        } catch (UnknownHostException e) {
        }
        return hostAddress;
    }

    public static Function<BodyReader, String> decodeBody() {
        return b -> {
            try {
                return b.decodeBodyToString(UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

}
