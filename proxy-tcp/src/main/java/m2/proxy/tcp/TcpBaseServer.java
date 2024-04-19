package m2.proxy.tcp;
import com.google.protobuf.ByteString;
import io.netty.channel.*;
import m2.proxy.tcp.handlers.ClientHandler;
import m2.proxy.tcp.handlers.ClientHandlerBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.m2.MessageOuterClass;

import java.security.KeyPair;

public class TcpBaseServer extends TcpBaseServerBase {

    private static final Logger log = LoggerFactory.getLogger(TcpBaseServer.class);

    public TcpBaseServer(int serverPort, String localAddress, KeyPair rsaKey) {
        super(serverPort,localAddress,rsaKey);
    }

    @Override
    public ClientHandlerBase setClientHandler(String channelId, ChannelHandlerContext ctx) {
        getClients().put(channelId, new ClientHandler(this, channelId, ctx) {
            @Override
            public void onRequest(long sessionId, long requestId, MessageOuterClass.RequestType type, ByteString request) {

            }
        });
        getClients().get(channelId).onInit();
        return getClients().get(channelId);
    }

}
