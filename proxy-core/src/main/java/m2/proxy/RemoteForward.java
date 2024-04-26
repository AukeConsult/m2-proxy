package m2.proxy;

import com.google.protobuf.ByteString;
import m2.proxy.common.ProxyStatus;
import m2.proxy.common.TcpException;
import m2.proxy.proto.MessageOuterClass.Message;
import m2.proxy.proto.MessageOuterClass.RequestType;
import m2.proxy.tcp.TcpBaseServerBase;
import m2.proxy.tcp.handlers.ConnectionHandler;
import m2.proxy.tcp.handlers.SessionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class RemoteForward extends TcpBaseServerBase {
    private static final Logger log = LoggerFactory.getLogger(RemoteForward.class);

    private final RawHttp http = new RawHttp();

    public Map<String, RemoteAccess> access;

    public RemoteForward(int tcpPort, Map<String, RemoteAccess> access) {
        super(tcpPort, Network.localAddress(), null);
        this.access=access;
    }

    public Optional<RawHttpResponse<?>> forwardTcp (RawHttpRequest request) throws TcpException {

        final String[] path = request.getStartLine().getUri().getPath().split("/");
        if(path.length>0) {

            log.info("forward path: {]",path);

            String clientId=path[0];
            if(access.containsKey(clientId)) {

                if(getClients().containsKey(clientId) &&  getClients().get(clientId).isOpen()) {

                    ConnectionHandler client = getClients().get(clientId);

                    long sessionId = request.getSenderAddress().isPresent() ?
                            UUID.nameUUIDFromBytes(
                                    request.getSenderAddress().toString().getBytes()
                                ).getMostSignificantBits()
                            : 1000L;

                    if(!client.getSessions().containsKey(sessionId)) {
                        client.openSession(new SessionHandler(sessionId) {
                            @Override
                            public void onReceive(long requestId, ByteString reply) {}
                        },10000);
                    }
                    SessionHandler session = client.getSessions().get(sessionId);

                    ByteString ret = session.sendRequest("", ByteString.copyFromUtf8(request.toString()), RequestType.HTTP,1000);
                    if(!ret.isEmpty()) {
                        return Optional.of(http.parseResponse(ret.toStringUtf8()));
                    }
                } else {
                    throw new TcpException(ProxyStatus.NOTOPEN,clientId);
                }
            }
        }
        return Optional.empty();

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
            public void onRequest(long sessionId, long requestId, RequestType type, String destination, ByteString request) {}
        };
    }
}
