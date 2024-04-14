package m2.proxy;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.m2.MessageOuterClass.*;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class NettyClient extends Netty {
    private static final Logger log = LoggerFactory.getLogger(NettyClient.class);

    private final String host;
    private final int port;
    private final String clientId;

    private EventLoopGroup group;

    public NettyClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.clientId = UUID.randomUUID().toString().substring(0,5);
        this.sessionId = rnd.nextLong();
        group = new NioEventLoopGroup();
    }

    public void start()  {

        log.info("client start");
        new Thread(() -> {

            try {
                Bootstrap b = new Bootstrap();
                b.group(group)
                        .channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ch.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws InvalidProtocolBufferException {
                                        Message m = readMessage(ctx, msg);
                                        printMessage(ctx.channel().id().asShortText(), m, ctx.channel().remoteAddress());
                                    }
                                    @Override
                                    public void channelActive(ChannelHandlerContext ctx) {
                                        log.info("Active, ch: {}, addr: {}",
                                                ctx.channel().id().asShortText(),
                                                ctx.channel().localAddress().toString()
                                        );
                                        sendMessage(ctx,"I am here");
                                        ctx.executor().scheduleAtFixedRate(() -> {
                                            sendMessage(ctx,"Hello server: " + System.currentTimeMillis());
                                        }, 0, 2, TimeUnit.SECONDS);

                                        ctx.executor().scheduleAtFixedRate(() -> {
                                            sendPing(ctx,clientId,ctx.channel().localAddress());
                                        }, 0, 10, TimeUnit.SECONDS);
                                    }

                                });
                            }
                        });

                ChannelFuture f = b.connect(host, port).sync();
                f.channel().closeFuture().sync();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        }).start();

    }

    public void stop() {
        group.shutdownGracefully();
        log.info("client stop");
    }
}
