package m2.proxy.server;

import m2.proxy.common.*;
import m2.proxy.executors.ServiceBaseExecutor;
import m2.proxy.server.remote.RemoteForward;
import m2.proxy.server.tcp.TcpForward;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.server.TcpRawHttpServer;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

public abstract class ProxyServer extends ServiceBaseExecutor implements Service {

    private static final Logger log = LoggerFactory.getLogger( ProxyServer.class );

    private final ProxyMetrics proxyMetrics = new ProxyMetrics();
    public ProxyMetrics getMetrics() { return proxyMetrics; }

    private final TcpRawHttpServer tcpRawHttpServer;

    private final int serverPort;
    private final RemoteForward remoteForward;
    private final TcpForward tcpForward;
    private final HttpHelper httpHelper = new HttpHelper();

    public int getServerPort() { return serverPort; }
    public RemoteForward getDirectForward() { return remoteForward; }

    public Optional<RawHttpResponse<?>> handleHttp(RawHttpRequest requestOut) throws HttpException {

        try {

            RawHttpRequest request = requestOut.eagerly();

            final String path = request.getStartLine().getUri().getPath();
            if(path.equals( "/" ) || path.equals( "/index.html" )) {

                String page= """
                <!DOCTYPE html><html><body><h1>Casa-IO</h1>
                <h2>Proxy-server</h>
                <p>Hello folks</p>
                <p>Server has #ACTIVE clients</p>
                <table>  
                <tr>    
                <th>ClientId</th>    
                <th>Address</th>    
                <th>Local Address</th>    
                <th>Local port</th>  
                </tr> 
                #CLIENTLIST</table></body></html>
                """;

                StringBuilder list = new StringBuilder();
                list.append( "<tr>" );
                tcpForward.getActiveClients().forEach( (k,v) -> {
                    list.append( "<td>" ).append( v.getRemoteClientId() ).append( "/td>" )
                            .append( "<td>").append( v.getRemotePublicAddress() ).append("/td>" )
                            .append( "<td>").append( v.getRemoteLocalAddress() ).append("/td>" )
                            .append( "<td>").append( v.getRemoteLocalPort() ).append("/td>" );
                });
                list.append( "</tr>" );

                page = page.replace( "#ACTIVE", String.valueOf(  tcpForward.getClientHandles().size() ))
                        .replace("#CLIENTLIST",list.toString());

                return new HttpHelper().response(new ContentResult( page ));

            } else {
                String body = "";
                if (request.getBody().isPresent()) {
                    body = request.getBody().map( httpHelper.decodeBody() ).orElse( "" );
                }
                Map<String, String> headers = new HashMap<>();
                request.getHeaders().forEach( (key, value) -> headers.put( key, value ) );
                String contentType = headers.getOrDefault( "Content-Type", "text/plain" );
                Optional<ContentResult> result = onHandlePath( request.getMethod(), path, headers, contentType, body );
                if (result.isPresent()) {
                    return httpHelper.response( result.get() );
                } else {
                    return Optional.empty();
                }
            }

        } catch (IOException e) {
            log.error( "Request: {}, IOException: {}", requestOut.getUri().getPath(), e.getMessage() );
            return httpHelper.errResponse( ProxyStatus.FAIL, e.getMessage() );
        } catch (Exception e) {
            log.error( "Request: {}, Exception: {}", requestOut.getUri().getPath(), e.getMessage() );
            throw new HttpException( ProxyStatus.FAIL, e.getMessage() );
        }
    }

    // define additional functions
    protected abstract Optional<ContentResult> onHandlePath(String verb, String path, Map<String, String> headers, String contentType, String body) throws HttpException;

    public ProxyServer(
            int serverPort,
            RemoteForward remoteForward,
            TcpForward tcpForward
    ) {

        this.serverPort = serverPort;
        this.remoteForward = remoteForward;
        this.tcpForward = tcpForward;

        this.remoteForward.setService( this );
        this.tcpForward.setService( this );

        this.proxyMetrics.setId( tcpForward.myId() );
        this.tcpRawHttpServer = new TcpRawHttpServer( serverPort );
    }

    @Override
    protected boolean open() {
        try {
            final HttpHelper httpHelper = new HttpHelper();
            tcpRawHttpServer.start( request -> {
                // check access keys forward with tcp
                try {

                    proxyMetrics.transIn.incrementAndGet();
                    // forward request using TCP proxy
                    Optional<RawHttpResponse<?>> remote = tcpForward.handleHttp( request );
                    if (remote.isPresent()) {
                        proxyMetrics.transRemoteOut.incrementAndGet();
                        return remote;
                    }
                    // forward http to remote site
                    Optional<RawHttpResponse<?>> direct = remoteForward.handleHttp( request );
                    if (direct.isPresent()) {
                        proxyMetrics.transDirectOut.incrementAndGet();
                        return direct;
                    }

                    // execute local commands
                    // provide path / and /indel.html for frontpage
                    Optional<RawHttpResponse<?>> local = handleHttp( request );
                    if (local.isPresent()) {
                        proxyMetrics.transLocalOut.incrementAndGet();
                        return local;
                    }

                    throw new HttpException( ProxyStatus.NOTFOUND, request.getUri().getPath() );

                } catch (HttpException e) {
                    proxyMetrics.transError.incrementAndGet();
                    log.error( "Request: {}, HttpException: {}", request.getUri().getPath(), e.getMessage() );
                    return httpHelper.errResponse( e.getStatus(), e.getMessage() );
                } catch (TcpException e) {
                    proxyMetrics.transError.incrementAndGet();
                    log.error( "Request: {}, TcpException: {}", request.getUri().getPath(), e.getMessage() );
                    return httpHelper.errResponse( e.getStatus(), e.getMessage() );
                }
            } );

            //getRemoteForward().start( Duration.ofSeconds( 2 ) );
            tcpForward.start( );
            if (!tcpForward.isRunning()) {
                log.error( "Proxy not starting" );
                return false;
            } else {
                RawHttp.waitForPortToBeTaken( serverPort, Duration.ofSeconds( 2 ) );
            }
            return true;
        } catch (TimeoutException e) {
            log.error( "Server cant start on port: {}, err: {}", serverPort, e.getMessage() );
            return false;
        }
    }

    @Override
    protected void startServices() { }
    @Override
    protected void execute() {
        while (isRunning()) {
            proxyMetrics.printLog();
            waitfor( 5000L );
        }
    }
    @Override
    protected void close() {
        tcpRawHttpServer.stop();
        proxyMetrics.printLog();
    }
    @Override
    protected void forceClose() { }
    @Override
    public Service getService() { return this; }
    @Override
    public void setService(Service service) { }
}
