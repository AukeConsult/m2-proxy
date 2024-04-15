package m2.proxy;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import proto.m2.MessageOuterClass;

import java.util.List;

public class BigMessageDecoder extends ByteToMessageDecoder {

    final public static int MESSAGE_ID = 901246789;
    final Netty server;
    public BigMessageDecoder(Netty server) {
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
                MessageOuterClass.Message m = MessageOuterClass.Message.parseFrom(buf);
                out.add(m);
            }
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }
}
