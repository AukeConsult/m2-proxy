package m2.proxy.server.remote;

import m2.proxy.common.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rawhttp.core.*;
import rawhttp.core.client.TcpRawHttpClient;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class RemoteForward implements Service {
    private static final Logger log = LoggerFactory.getLogger( RemoteForward.class );

    private final TcpRawHttpClient tcpRawHttpClient = new TcpRawHttpClient();
    private final HttpHelper httpHelper = new HttpHelper();
    public final Map<String, RemoteSite> sites = new ConcurrentHashMap<>();
    public Map<String, RemoteSite> getSites() { return sites; }

    public RemoteForward() { }

    // forward http requests directly to another site
    public Optional<RawHttpResponse<?>> handleHttp(RawHttpRequest request) throws HttpException {

        for (RemoteSite site : sites.values()) {
            Optional<RawHttpRequest> requestOut = httpHelper.pathDirectSite( site, request );
            if(requestOut.isPresent()) {
                log.info("Direct Forward {}",requestOut.get().getStartLine().getUri().toString());
                try {
                    RawHttpResponse<?> response = tcpRawHttpClient.send(
                            httpHelper.parseRequest( requestOut.get().eagerly().toString() )
                    );
                    return Optional.of( response.eagerly() );
                } catch (IOException e) {
                    throw new HttpException( ProxyStatus.FAIL, e.getMessage() );
                }
            }
        }
        return Optional.empty();
    }
    private Service service;
    @Override
    public Service getService() { return service; }
    @Override
    public void setService(Service service) { }
}
