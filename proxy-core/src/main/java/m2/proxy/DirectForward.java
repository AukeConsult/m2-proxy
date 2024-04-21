package m2.proxy;

import m2.proxy.common.HttpException;
import m2.proxy.common.ProxyStatus;
import rawhttp.core.RawHttpHeaders;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.client.TcpRawHttpClient;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class DirectForward extends Forward {

    private final TcpRawHttpClient tcpRawHttpClient = new TcpRawHttpClient();
    public Map<String, DirectSite> sites = new ConcurrentHashMap<>();

    public DirectForward() {}

    public Optional<RawHttpResponse<?>> forwardHttp (RawHttpRequest request) throws HttpException {

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
                    return Optional.of(response.eagerly());
                } catch (IOException e) {
                    throw new HttpException(ProxyStatus.FAIL,e.getMessage());
                }
            }
        }
        return Optional.empty();

    }

    public Optional<RawHttpResponse<?>> forwardHttpDirect (RawHttpRequest request) throws HttpException {

        for (DirectSite s : sites.values()) {
            if (s.path.isEmpty()) {
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
                    return Optional.of(response.eagerly());
                } catch (IOException e) {
                    throw new HttpException(ProxyStatus.FAIL,e.getMessage());
                }
            }
        }
        return Optional.empty();
    }

}
