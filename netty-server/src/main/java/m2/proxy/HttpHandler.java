package m2.proxy;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.m2.MessageOuterClass.*;

import java.util.Random;

public class HttpHandler {
    private static final Logger log = LoggerFactory.getLogger(NettyClient.class);

    private final Netty server;
    public HttpHandler(Netty server) {
        this.server=server;
    }
    public void executeRequest(final ChannelHandlerContext ctx, final HttpInRequest request) {

        server.getExecutor().execute(() -> {
            log.info("HTTP: request: {}, Session: {}",
                    request.getRequestId(),
                    request.getSessionId()
            );
            try {
                Thread.sleep(new Random().nextInt(2000));
            } catch (InterruptedException ignored) {
            }

            HttpOutResponse r = HttpOutResponse.newBuilder()
                    .setRequestId(request.getRequestId())
                    .setSessionId(request.getSessionId())
                    .build();

            ctx.writeAndFlush(Unpooled.wrappedBuffer(
                    Message.newBuilder()
                            .setType(MessageType.HTTP_RESPONSE)
                            .setSubMessage(r.toByteString())
                            .build()
                            .toByteArray()
            ));

        });
    }
}
