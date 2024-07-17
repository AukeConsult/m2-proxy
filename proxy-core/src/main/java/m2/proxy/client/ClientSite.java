package m2.proxy.client;

import m2.proxy.common.*;
import m2.proxy.executors.ServiceBase;
import m2.proxy.server.remote.RemoteForward;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.server.TcpRawHttpServer;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

public class ClientSite extends ServiceBase {

    private static final Logger log = LoggerFactory.getLogger( ClientSite.class );
    HttpHelper httpHelper = new HttpHelper();

    private final ProxyMetrics proxyMetrics = new ProxyMetrics();
    public ProxyMetrics getMetrics() { return proxyMetrics; }

    private final int sitePort;
    private final RemoteForward remoteForward;
    private final TcpRawHttpServer tcpRawHttpServer;
    private final ProxyClient server;

    public int getSitePort() { return sitePort; }

    public ClientSite(
            ProxyClient server,
            int sitePort,
            RemoteForward remoteForward
    ) {
        this.server=server;
        this.remoteForward = remoteForward;
        this.sitePort=sitePort;
        tcpRawHttpServer = new TcpRawHttpServer( sitePort );
    }

    public Optional<ContentResult> getFrontPage() {

        String page= """
                <!DOCTYPE html><html><body><h1>Casa-IO</h1>
                <h2>Proxy-server</h>
                <p>Hello folks</p>
                <table>  
                <tr>    
                <th>accessPath</th>    
                <th>remoteAddress</th>    
                </tr> 
                #ACCESSLIST</table></body></html>
                """;

        StringBuilder list = new StringBuilder();
//        list.append( "<tr>" );
//        server.getRemoteForward().getActiveClients().forEach( (k,v) -> {
//            list.append( "<td>" ).append( v.getClientId() ).append( "/td>" )
//                    .append( "<td>").append( v.getRemoteAddress() ).append("/td>" )
//                    .append( "<td>").append( v.getRemoteLocalAddress() ).append("/td>" )
//                    .append( "<td>").append( v.getRemoteLocalPort() ).append("/td>" );
//        });
//        list.append( "</tr>" );

        page = page.replace("#ACCESSLIST",list.toString());

        return Optional.of(new ContentResult( page ));
    }

    public Optional<RawHttpResponse<?>> handleHttp(RawHttpRequest request) throws HttpException {
        try {

            final String path = request.getStartLine().getUri().getPath();

            Optional<ContentResult> result = Optional.empty();
            if(path.equals( "/" )) {
                result = getFrontPage();
            } else if(path.equals( "/index.html" )) {
                result = getFrontPage();
            }

            if (result.isPresent()) {
                return httpHelper.response(result.get());
            } else {
                return Optional.empty();
            }
        } catch (Exception e) {
            throw new HttpException( ProxyStatus.FAIL, e.getMessage() );
        }
    }

    @Override
    public void start() {

        try {
            final HttpHelper httpHelper = new HttpHelper();
            tcpRawHttpServer.start( request -> {
                // check access keys forward with tcp
                proxyMetrics.transIn.incrementAndGet();
                try {
                    Optional<RawHttpResponse<?>> direct = remoteForward.handleHttp( request );
                    if (direct.isPresent()) {
                        proxyMetrics.transDirectOut.incrementAndGet();
                        return direct;
                    }
                    RawHttpRequest requestOut = request.eagerly();
                    // server fixed
                    Optional<RawHttpResponse<?>> server = handleHttp( requestOut );
                    if (server.isPresent()) {
                        proxyMetrics.transServerOut.incrementAndGet();
                        return server;
                    }
//                    // execute local replies
//                    Optional<RawHttpResponse<?>> local = localSite.handleHttp( requestOut );
//                    if (local.isPresent()) {
//                        proxyMetrics.transLocalOut.incrementAndGet();
//                        return local;
//                    }
                    proxyMetrics.transError.incrementAndGet();
                    throw new HttpException( ProxyStatus.NOTFOUND, requestOut.getUri().getPath() );
                } catch (HttpException e) {
                    log.error( "Request: {}, HttpException: {}", request.getUri().getPath(), e.getMessage() );
                    return httpHelper.errResponse( e.getStatus(), e.getMessage() );
                } catch (IOException e) {
                    log.error( "Request: {}, IOException: {}", request.getUri().getPath(), e.getMessage() );
                    return httpHelper.errResponse( ProxyStatus.FAIL, e.getMessage() );
                }
            } );
            RawHttp.waitForPortToBeTaken( sitePort, Duration.ofSeconds( 2 ) );

        } catch (TimeoutException e) {
            log.error( "Server cant start on port: {}, err: {}", sitePort, e.getMessage() );

        }

    }
    @Override
    public void stop() {
        tcpRawHttpServer.stop();
    }
}
