package m2.proxy;

import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.server.TcpRawHttpServer;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

public class ProxyServer {

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
            Optional<RawHttpResponse<?>> remote = remoteForward.forward(request);
            if(remote.isPresent()) { return remote; }

            // check http direct forward
            Optional<RawHttpResponse<?>> local = directForward.forward(request);
            if(local.isPresent()) { return local; }

            // execute local replies
            return localSite.forward(request);

        });
        RawHttp.waitForPortToBeTaken(serverPort, Duration.ofSeconds(2));
    }

    public void stop() {
        tcpRawHttpServer.stop();
    }



}
