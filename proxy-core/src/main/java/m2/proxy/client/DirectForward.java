package m2.proxy.client;

import m2.proxy.common.Forward;
import m2.proxy.common.HttpException;
import m2.proxy.common.HttpHelper;
import m2.proxy.common.ProxyStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rawhttp.core.*;
import rawhttp.core.client.TcpRawHttpClient;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class DirectForward extends Forward {
    private static final Logger log = LoggerFactory.getLogger( DirectForward.class );

    private final TcpRawHttpClient tcpRawHttpClient = new TcpRawHttpClient();
    private final HttpHelper httpHelper = new HttpHelper();
    public final Map<String, DirectSite> sites = new ConcurrentHashMap();
    public Map<String, DirectSite> getSites() {
        return sites;
    }

    public DirectForward() { }

    public Optional<RawHttpResponse<?>> handleHttp(RawHttpRequest request) throws HttpException {

        for (DirectSite s : sites.values()) {
            Optional<RawHttpRequest> requestOut = httpHelper.forward( s, request );
            if(requestOut.isPresent()) {
                log.info("Direct Forward {}",requestOut.get().getStartLine().getUri().toString());
                try {
                    RawHttpResponse<?> response = tcpRawHttpClient.send(
                            http.parseRequest( requestOut.get().eagerly().toString() )
                    );
                    return Optional.of( response.eagerly() );
                } catch (IOException e) {
                    throw new HttpException( ProxyStatus.FAIL, e.getMessage() );
                }
            }
        }
        return Optional.empty();
    }

}
