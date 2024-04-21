package m2.proxy;

import com.google.protobuf.ByteString;
import m2.proxy.common.HttpException;
import m2.proxy.tcp.TcpBase;
import m2.proxy.tcp.TcpBaseClientBase;
import m2.proxy.tcp.handlers.ConnectionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import m2.proxy.proto.MessageOuterClass.*;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;

import java.util.Optional;

public class ProxyClient extends TcpBaseClientBase {

    private static final Logger log = LoggerFactory.getLogger(ProxyClient.class);

    private final DirectForward directForward;
    private final LocalSite localSite;

    public ProxyClient(
            String clientId,
            String serverAddr,
            int tcpPort,
            DirectForward directForward,
            LocalSite localSite
    ) {
        super(clientId, serverAddr, tcpPort, Network.localAddress());
        this.directForward = directForward;
        this.localSite = localSite;
    }

    @Override
    public ConnectionHandler setConnectionHandler() {
        return new ConnectionHandler() {
            @Override
            protected void onMessageIn(Message m) {}
            @Override
            protected void onMessageOut(Message m) {}
            @Override
            protected void onConnect(String ClientId, String remoteAddress) {}
            @Override
            protected void onDisconnect(String ClientId) {}
            @Override
            protected void onRequest(long sessionId, long requestId, RequestType type, String address, ByteString requestBytes) {
                getServer().getTaskPool().execute(() -> {
                    try {
                        RawHttpRequest request = directForward.makeRequest(requestBytes.toStringUtf8());
                        Optional<RawHttpResponse<?>> ret = directForward.forwardHttp(request);
                        if(!ret.isPresent()) {
                            ret = directForward.forwardHttp(request);
                        }
                        Thread.sleep(TcpBase.rnd.nextInt(10)+1);
                        if(ret.isPresent()) {
                            ByteString reply = ByteString.copyFromUtf8(ret.get().toString());
                            log.info("Reply -> session: {}, id: {}, type: {}",sessionId,requestId,type);
                            reply(sessionId,requestId,type,reply);
                        } else {
                            ByteString reply = ByteString.copyFromUtf8(directForward.makeErrorReply("not found" + request.getUri()).toString());
                            reply(sessionId,requestId,type,reply);

                        }
                    } catch (HttpException | InterruptedException e) {
                        log.warn("Error request: {}",e.getMessage());
                        ByteString reply = ByteString.copyFromUtf8(directForward.makeErrorReply( e.getMessage()).toString());
                        reply(sessionId,requestId,type,reply);
                    }
                });
            }
        };
    }
}
