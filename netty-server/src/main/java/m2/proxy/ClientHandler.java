package m2.proxy;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.m2.MessageOuterClass.*;

import java.net.SocketAddress;

public class ClientHandler {

    private static final Logger log = LoggerFactory.getLogger(NettyClient.class);

    private final ChannelId channelId;
    private final ChannelHandlerContext ctx;
    private final SocketAddress remoteAddr;

    private String localHost;
    private int localPost;
    private String clientId;

    public ClientHandler(ChannelId channelId, ChannelHandlerContext ctx) {
        this.channelId=channelId;
        this.ctx=ctx;
        this.remoteAddr=ctx.channel().remoteAddress();
    }

    public void prosessMessage(Message m)  {
        if(m.getType()== MessageType.PING) {
            clientId = m.getPing().getClientId();
            localHost = m.getPing().getLocalHost();
        }
    }


}
