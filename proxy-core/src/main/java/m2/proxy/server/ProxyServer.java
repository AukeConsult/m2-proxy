package m2.proxy.server;

import m2.proxy.common.DirectForward;
import m2.proxy.common.LocalForward;
import m2.proxy.common.*;
import m2.proxy.executors.ServiceBaseExecutor;
import m2.proxy.common.ProxyMetrics;
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

public class ProxyServer extends ServiceBaseExecutor implements Service {

    private static final Logger log = LoggerFactory.getLogger( ProxyServer.class );

    private final ProxyMetrics proxyMetrics = new ProxyMetrics();
    public ProxyMetrics getMetrics() { return proxyMetrics; }

    private final TcpRawHttpServer tcpRawHttpServer;

    private final int serverPort;
    private final LocalForward localForward;
    private final DirectForward directForward;
    private final TcpForward tcpForward;
    private final ServerSite serverSite;

    public int getServerPort() { return serverPort; }
    public DirectForward getDirectForward() { return directForward; }
    public TcpForward getRemoteForward() { return tcpForward; }
    public LocalForward getLocalSite() { return localForward; }

    public ProxyServer(
            int serverPort,
            DirectForward directForward,
            TcpForward tcpForward,
            LocalForward localForward
    ) {

        this.serverPort = serverPort;
        this.directForward = directForward;
        this.tcpForward = tcpForward;
        this.localForward = localForward;

        this.directForward.setService( this );
        this.tcpForward.setService( this );
        this.localForward.setService( this );

        this.serverSite = new ServerSite( this );
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
                    Optional<RawHttpResponse<?>> remote = tcpForward.handleHttp( request );
                    if (remote.isPresent()) {
                        proxyMetrics.transRemoteOut.incrementAndGet();
                        return remote;
                    }
                    Optional<RawHttpResponse<?>> direct = directForward.handleHttp( request );
                    if (direct.isPresent()) {
                        proxyMetrics.transDirectOut.incrementAndGet();
                        return direct;
                    }
                    RawHttpRequest requestOut = request.eagerly();
                    // server fixed
                    Optional<RawHttpResponse<?>> server = serverSite.handleHttp( requestOut );
                    if (server.isPresent()) {
                        proxyMetrics.transServerOut.incrementAndGet();
                        return server;
                    }
                    // execute local replies
                    Optional<RawHttpResponse<?>> local = localForward.handleHttp( requestOut );
                    if (local.isPresent()) {
                        proxyMetrics.transLocalOut.incrementAndGet();
                        return local;
                    }
                    throw new HttpException( ProxyStatus.NOTFOUND, requestOut.getUri().getPath() );
                } catch (HttpException e) {
                    proxyMetrics.transError.incrementAndGet();
                    log.error( "Request: {}, HttpException: {}", request.getUri().getPath(), e.getMessage() );
                    return httpHelper.errResponse( e.getStatus(), e.getMessage() );
                } catch (TcpException e) {
                    proxyMetrics.transError.incrementAndGet();
                    log.error( "Request: {}, TcpException: {}", request.getUri().getPath(), e.getMessage() );
                    return httpHelper.errResponse( e.getStatus(), e.getMessage() );
                } catch (IOException e) {
                    log.error( "Request: {}, IOException: {}", request.getUri().getPath(), e.getMessage() );
                    return httpHelper.errResponse( ProxyStatus.FAIL, e.getMessage() );
                }
            } );

            getRemoteForward().start( Duration.ofSeconds( 2 ) );
            if (!getRemoteForward().isRunning()) {
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
        getRemoteForward().stop();
        proxyMetrics.printLog();
    }
    @Override
    protected void forceClose() { }
    @Override
    public Service getService() { return this; }
    @Override
    public void setService(Service service) { }
}
