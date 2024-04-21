package m2.proxy;

import m2.proxy.common.HttpException;
import m2.proxy.common.TcpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.server.TcpRawHttpServer;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

public class ProxyServer {

    private static final Logger log = LoggerFactory.getLogger(ProxyServer.class);

    private final TcpRawHttpServer tcpRawHttpServer;
    private final int serverPort;

    private final DirectForward directForward;
    private final RemoteForward remoteForward;
    private final LocalSite localSite;

    public ProxyServer(int serverPort,
                       DirectForward directForward,
                       RemoteForward remoteForward,
                       LocalSite localSite
    ) {
        this.serverPort=serverPort;
        this.directForward = directForward;
        this.remoteForward = remoteForward;
        this.localSite = localSite;
        this.tcpRawHttpServer = new TcpRawHttpServer(serverPort);
    }

    public void start() throws TimeoutException {

        tcpRawHttpServer.start(request -> {
            // check access keys forward with tcp
            try {

                Optional<RawHttpResponse<?>> remote = remoteForward.forwardTcp(request);
                if(remote.isPresent()) { return remote; }

                Optional<RawHttpResponse<?>> local = directForward.forwardHttp(request);
                if(local.isPresent()) { return local; }

                // execute local replies
                return localSite.forwardHttp(request);

            } catch (HttpException e) {
                return Optional.of(directForward.makeErrorReply(e.getMessage()));
            } catch (TcpException e) {
                log.info("send request: {}",e.getMessage());
                return Optional.of(directForward.makeErrorReply(e.getMessage()));
            }


        });
        RawHttp.waitForPortToBeTaken(serverPort, Duration.ofSeconds(2));
    }

    public void stop() {
        tcpRawHttpServer.stop();
    }



}
