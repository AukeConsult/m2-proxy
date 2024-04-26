package m2.proxy.tcp.handlers;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import m2.proxy.proto.MessageOuterClass.Message;
import m2.proxy.tcp.TcpBase;

import java.util.List;

public class MessageDecoder extends ByteToMessageDecoder {

    final public static int MESSAGE_ID = 901246789;
    final TcpBase server;
    public MessageDecoder(TcpBase server) {
        this.server=server;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        try {
            if(in.readableBytes() < 4) {
                return;
            }
            in.markReaderIndex();
            if(in.readInt()==MESSAGE_ID) {
                int length = in.readInt();
                if(in.readableBytes()<length) {
                    in.resetReaderIndex();
                    return;
                }
                byte[] buf = new byte[length];
                in.readBytes(length).getBytes(0,buf);
                Message m = Message.parseFrom(buf);
                out.add(m);
            }
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }
}
