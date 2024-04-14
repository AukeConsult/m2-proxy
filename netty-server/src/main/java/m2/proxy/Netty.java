package m2.proxy;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.m2.MessageOuterClass.*;

import java.net.SocketAddress;
import java.util.Random;

public abstract class Netty {
    private static final Logger log = LoggerFactory.getLogger(NettyClient.class);

    static Random rnd = new Random();

    public long sessionId = rnd.nextLong();

    public Message readMessage(ChannelHandlerContext ctx, ByteBuf msg) throws InvalidProtocolBufferException {
        byte[] buf = new byte[msg.readableBytes()];
        msg.getBytes(0,buf);
        return Message.parseFrom(buf);
    }

    public void printMessage(String id, Message m, SocketAddress remote)  {

        if(m.getType()== MessageType.MESSAGE) {
            log.info("MESSAGE ch: {}, m: {}, addr: {}",
                    id,
                    m.getMessage(),
                    remote.toString()
            );
        } else if(m.getType()== MessageType.PING) {
            log.info("PING ch: {}, clientId: {}, localAddr: {}, addr: {}",
                    id,
                    m.getPing().getClientId(),
                    m.getPing().getLocalHost(),
                    remote.toString()
            );
        }
    }


    public void sendMessage(ChannelHandlerContext ctx, String msg) {
        ctx.writeAndFlush(Unpooled.wrappedBuffer(
                Message.newBuilder()
                        .setType(MessageType.MESSAGE)
                        .setMessage(msg)
                        .setSessionId(this.sessionId)
                        .setRequestId(rnd.nextLong())
                        .build()
                        .toByteArray()
        ));
    }
    public void sendPing(ChannelHandlerContext ctx, String clientId, SocketAddress address) {
        ctx.writeAndFlush(Unpooled.wrappedBuffer(
                Message.newBuilder()
                        .setType(MessageType.PING)
                        .setPing(Ping.newBuilder()
                                .setClientId(clientId)
                                .setLocalHost(address.toString())
                                .build()
                        )
                        .setRequestId(rnd.nextLong())
                        .setSessionId(this.sessionId)
                        .build()
                        .toByteArray()
        ));
    }

}
