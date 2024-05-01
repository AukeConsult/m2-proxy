package m2.proxy.tcp.handlers;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import m2.proxy.proto.MessageOuterClass.Message;
import m2.proxy.tcp.TcpBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MessageDecoder extends ByteToMessageDecoder {
    private static final Logger log = LoggerFactory.getLogger( ConnectionHandler.class );

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
            int length = in.readInt();
            if(in.readableBytes()<length) {
                in.resetReaderIndex();
                return;
            }
            byte[] buf = new byte[length];
            in.readBytes(length).getBytes(0,buf);
            out.add(buf);

        } catch (Exception e) {
            log.error("error reading data",e);
        }
    }
}
