package m2.proxy.server;

import com.google.protobuf.ByteString;
import m2.proxy.common.HttpHelper;
import m2.proxy.common.Network;
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
import java.util.concurrent.ConcurrentHashMap;

public class RemoteForward extends TcpBaseServerBase {
    private static final Logger log = LoggerFactory.getLogger(RemoteForward.class);

    private final RawHttp http = new RawHttp();
    private final HttpHelper httpHelper = new HttpHelper();

    public final Map<String, RemoteAccess> access = new ConcurrentHashMap<>();
    public Map<String, RemoteAccess> getAccess() {
        return access;
    }

    private final int timeOut;

    public RemoteForward(int tcpPort, int timeOut) {
        super(tcpPort, Network.localAddress(), null);
        this.timeOut = timeOut;
    }

    public Optional<RawHttpResponse<?>> handleHttp(RawHttpRequest request) throws TcpException {
        String accessKey = httpHelper.getAccessPath(request);
        if (!accessKey.isEmpty()) {
            if (access.containsKey(accessKey)) {
                log.info("forward key: {}", accessKey);
                String clientId = access.get(accessKey).getClientId();
                if (getClients().containsKey(clientId) && getClients().get(clientId).isOpen()) {
                    ConnectionHandler client = getClients().get(clientId);
                    long sessionId = request.getSenderAddress().isPresent() ?
                            UUID.nameUUIDFromBytes(
                                    request.getSenderAddress().toString().getBytes()
                                                  ).getMostSignificantBits()
                            : 1000L;
                    if (!client.getSessions().containsKey(sessionId)) {
                        client.openSession(new SessionHandler(sessionId) {
                            @Override
                            public void onReceive(long requestId, ByteString reply) {
                            }
                        }, 10000);
                    }
                    SessionHandler session = client.getSessions().get(sessionId);
                    Optional<RawHttpRequest> requestOut = httpHelper.forward( accessKey, request );
                    if(requestOut.isPresent()) {
                        log.info("Direct Forward {}",requestOut.get().getStartLine().getUri().toString());
                        ByteString ret = session.sendRequest(
                                "", ByteString.copyFromUtf8(requestOut.get().toString()), RequestType.HTTP, timeOut);
                        if (!ret.isEmpty()) {
                            return Optional.of(http.parseResponse(ret.toStringUtf8()));
                        }
                    } else {
                        log.warn("No request forward: {}",request.getStartLine().getUri().toString());
                    }
                } else {
                    throw new TcpException(ProxyStatus.NOTOPEN, clientId);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public ConnectionHandler setConnectionHandler() {
        return new ConnectionHandler() {
            @Override
            protected void onMessageIn(Message m) {
            }
            @Override
            protected void onMessageOut(Message m) {
            }
            @Override
            protected void onConnect(String ClientId, String remoteAddress) {
            }
            @Override
            protected void onDisconnect(String ClientId) {
            }
            @Override
            public void onRequest(long sessionId, long requestId, RequestType type, String destination, ByteString request) {
            }
        };
    }
}
