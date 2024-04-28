package m2.proxy.server;

import m2.proxy.client.DirectForward;
import m2.proxy.client.LocalSite;
import m2.proxy.common.HttpException;
import m2.proxy.common.TcpException;
import m2.proxy.executors.ServiceBaseExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.server.TcpRawHttpServer;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class ProxyServer extends ServiceBaseExecutor {

    private static final Logger log = LoggerFactory.getLogger( ProxyServer.class );

    private final TcpRawHttpServer tcpRawHttpServer;

    private final int serverPort;
    private final DirectForward directForward;
    private final RemoteForward remoteForward;
    private final LocalSite localSite;

    public int getServerPort() { return serverPort; }
    public DirectForward getDirectForward() { return directForward; }
    public RemoteForward getRemoteForward() { return remoteForward; }
    public LocalSite getLocalSite() { return localSite; }

    private AtomicInteger transIn = new AtomicInteger();
    private AtomicInteger transRemoteOut = new AtomicInteger();
    private AtomicInteger transDirectOut = new AtomicInteger();
    private AtomicInteger transLocalOut = new AtomicInteger();
    private AtomicInteger transError = new AtomicInteger();

    public ProxyServer(
            int serverPort,
            DirectForward directForward,
            RemoteForward remoteForward,
            LocalSite localSite
                      ) {
        this.serverPort = serverPort;
        this.directForward = directForward;
        this.remoteForward = remoteForward;
        this.localSite = localSite;
        this.tcpRawHttpServer = new TcpRawHttpServer( serverPort );
    }

    @Override
    protected boolean open() {
        try {

            tcpRawHttpServer.start( request -> {
                // check access keys forward with tcp
                try {
                    transIn.incrementAndGet();
                    Optional<RawHttpResponse<?>> remote = remoteForward.handleHttp( request );
                    if (remote.isPresent()) {
                        transRemoteOut.incrementAndGet();
                        return remote;
                    }
                    Optional<RawHttpResponse<?>> local = directForward.handleHttp( request );
                    if (local.isPresent()) {
                        transDirectOut.incrementAndGet();
                        return local;
                    }
                    // execute local replies
                    transLocalOut.incrementAndGet();
                    return localSite.handleHttp( request );
                } catch (HttpException e) {
                    transError.incrementAndGet();
                    log.info( "Send direct request error: {}", e.getMessage() );
                    return Optional.of( directForward.makeErrorReply( e.getMessage() ) );
                } catch (TcpException e) {
                    transError.incrementAndGet();
                    log.info( "Send remote request error: {}", e.getMessage() );
                    return Optional.of( directForward.makeErrorReply( e.getMessage() ) );
                }
            } );
            RawHttp.waitForPortToBeTaken( serverPort, Duration.ofSeconds( 2 ) );
            return true;

        } catch (TimeoutException e) {
            log.error( "Server cant start on port: {}, err: {}",serverPort, e.getMessage());
            return false;
        }
    }
    @Override
    protected void startServices() {

    }
    @Override
    protected void execute() {
        while(isRunning()) {

            log.info( "Number of transactions in: {}, err: {}, OUT-> remote: {}, direct: {}, local: {}",
                      transIn.get(),
                      transError.get(),
                      transRemoteOut.get(),
                      transDirectOut.get(),
                      transLocalOut.get()
                    );

            waitfor( 5000L );
        }

    }
    @Override
    protected void close() {
        tcpRawHttpServer.stop();
    }
    @Override
    protected void forceClose() { }
}
