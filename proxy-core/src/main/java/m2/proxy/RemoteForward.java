package m2.proxy;

import com.google.protobuf.ByteString;
import io.netty.channel.ChannelHandlerContext;
import m2.proxy.tcp.TcpBaseServerBase;
import m2.proxy.tcp.handlers.ClientHandler;
import m2.proxy.tcp.handlers.ClientHandlerBase;
import m2.proxy.tcp.handlers.SessionHandler;
import proto.m2.MessageOuterClass.*;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class RemoteForward extends TcpBaseServerBase {

    private final RawHttp http = new RawHttp();

    public Map<String, RemoteAccess> access;

    public RemoteForward(int serverPort, String localAddress, Map<String, RemoteAccess> access) {
        super(serverPort, localAddress, null);
        this.access=access;
    }

    public Optional<RawHttpResponse<?>> forward (RawHttpRequest request) {

        final String[] path = request.getStartLine().getUri().getPath().split("/");

        if(path.length>0) {

            String clientId=path[0];
            if(access.containsKey(clientId)) {

                if(getClients().containsKey(clientId) &&  getClients().get(clientId).isOpen()) {

                    ClientHandlerBase client = getClients().get(clientId);

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
                    ByteString ret = session.sendRequest("",ByteString.copyFromUtf8(request.toString()), RequestType.HTTP,1000);
                    if(!ret.isEmpty()) {
                        return Optional.of(http.parseResponse(ret.toStringUtf8()));
                    }
                } else {
                    // client not open
                }
            }
        }
        return Optional.of(null);

    }

    @Override
    public ClientHandlerBase setClientHandler(String id, ChannelHandlerContext ctx) {
        return new ClientHandler(this, id, ctx) {
            @Override
            public boolean isOpen() {return false;}
            @Override
            public void onRequest(long sessionId, long requestId, RequestType type, String destination, ByteString request) {}
        };
    }
}
