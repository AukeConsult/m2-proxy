package m2.proxy.server;

import m2.proxy.client.DirectForward;
import m2.proxy.client.LocalForward;
import m2.proxy.common.HttpException;
import m2.proxy.common.HttpHelper;
import m2.proxy.common.ProxyStatus;
import m2.proxy.common.TcpException;
import m2.proxy.executors.ServiceBaseExecutor;
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

public class ProxyServer extends ServiceBaseExecutor {

    private static final Logger log = LoggerFactory.getLogger( ProxyServer.class );

    private final TcpRawHttpServer tcpRawHttpServer;
    private final ServerMetrics metrics = new ServerMetrics();

    private final int serverPort;
    private final LocalForward localForward;
    private final DirectForward directForward;
    private final RemoteForward remoteForward;
    private final ServerSite serverSite;

    public int getServerPort() { return serverPort; }
    public DirectForward getDirectForward() { return directForward; }
    public RemoteForward getRemoteForward() { return remoteForward; }
    public LocalForward getLocalSite() { return localForward; }

    public ProxyServer(
            int serverPort,
            DirectForward directForward,
            RemoteForward remoteForward,
            LocalForward localForward
    ) {

        this.serverPort = serverPort;
        this.directForward = directForward;
        this.remoteForward = remoteForward;
        this.localForward = localForward;

        this.directForward.setServer( this );
        this.remoteForward.setServer( this );
        this.localForward.setServer( this );

        this.serverSite = new ServerSite( this );
        this.tcpRawHttpServer = new TcpRawHttpServer( serverPort );
    }

    @Override
    protected boolean open() {
        try {
            final HttpHelper httpHelper = new HttpHelper();
            tcpRawHttpServer.start( request -> {
                // check access keys forward with tcp
                try {
                    metrics.transIn.incrementAndGet();
                    Optional<RawHttpResponse<?>> remote = remoteForward.handleHttp( request );
                    if (remote.isPresent()) {
                        metrics.transRemoteOut.incrementAndGet();
                        return remote;
                    }
                    Optional<RawHttpResponse<?>> direct = directForward.handleHttp( request );
                    if (direct.isPresent()) {
                        metrics.transDirectOut.incrementAndGet();
                        return direct;
                    }
                    RawHttpRequest requestOut = request.eagerly();
                    // server fixed
                    Optional<RawHttpResponse<?>> server = serverSite.handleHttp( requestOut );
                    if (server.isPresent()) {
                        metrics.transServerOut.incrementAndGet();
                        return server;
                    }
                    // execute local replies
                    Optional<RawHttpResponse<?>> local = localForward.handleHttp( requestOut );
                    if (local.isPresent()) {
                        metrics.transLocalOut.incrementAndGet();
                        return local;
                    }
                    throw new HttpException( ProxyStatus.NOTFOUND, requestOut.getUri().getPath() );
                } catch (HttpException e) {
                    metrics.transError.incrementAndGet();
                    log.error( "Request: {}, HttpException: {}", request.getUri().getPath(), e.getMessage() );
                    return httpHelper.errResponse( e.getStatus(), e.getMessage() );
                } catch (TcpException e) {
                    metrics.transError.incrementAndGet();
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
            metrics.printLog();
            waitfor( 5000L );
        }
    }
    @Override
    protected void close() {
        tcpRawHttpServer.stop();
        getRemoteForward().stop();
    }
    @Override
    protected void forceClose() { }
}
