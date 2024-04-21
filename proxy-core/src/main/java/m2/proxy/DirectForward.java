package m2.proxy;

import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpHeaders;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.client.TcpRawHttpClient;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class DirectForward {

    private final TcpRawHttpClient tcpRawHttpClient = new TcpRawHttpClient();
    private final RawHttp http = new RawHttp();

    public Map<String, DirectSite> sites = new ConcurrentHashMap<>();

    public DirectForward() {}

    public Optional<RawHttpResponse<?>> forward (RawHttpRequest request) {

        final String path = request.getStartLine().getUri().getPath();

        for (DirectSite s : sites.values()) {
            if (path.startsWith(s.path)) {

                request = request.withRequestLine(
                        request.getStartLine().withHost(s.destination)
                ).withHeaders(RawHttpHeaders.newBuilder()
                        .with("X-Forwarded-For", request.getSenderAddress().orElseThrow(() ->
                                new IllegalStateException("client has no IP")).getHostAddress())
                        .build());

                // Forward the request to the end server
                try {
                    RawHttpResponse<?> response = tcpRawHttpClient.send(
                            http.parseRequest(request.eagerly().toString())
                    );
                    return Optional.of(response);
                } catch (IOException e) {
                    //TOD make error response
                    throw new RuntimeException(e);
                }
            }
        }
        return Optional.empty();

    }

}
