package m2.proxy;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.m2.MessageOuterClass.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class NettyServer extends Netty {

    private static final Logger log = LoggerFactory.getLogger(NettyServer.class);
    private final int port;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    private Map<ChannelId,ClientHandler> clients = new ConcurrentHashMap<>();

    public NettyServer(int port) {
        this.port = port;
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();
    }

    public void start()  {

        log.info("server start");

        new Thread(() -> {
            try {

                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ch.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
                                        Message m = readMessage(ctx, msg);
                                        clients.get(ctx.channel().id()).prosessMessage(m);
                                        printMessage(ctx.channel().id().asShortText(), m, ctx.channel().remoteAddress());
                                    }

                                    @Override
                                    public void channelActive(ChannelHandlerContext ctx) {
                                        log.info("active client ch: {}, addr: {}",
                                                ctx.channel().id().asShortText(),
                                                ctx.channel().remoteAddress().toString()
                                        );
                                        clients.put(ctx.channel().id(),new ClientHandler(ctx.channel().id(),ctx));
                                        ctx.executor().scheduleAtFixedRate(() -> {
                                            sendMessage(ctx,"Hello from server: " + System.currentTimeMillis());
                                        }, 0, 15, TimeUnit.SECONDS);
                                    }

                                });
                            }
                        })
                        .option(ChannelOption.SO_BACKLOG, 128)
                        .childOption(ChannelOption.SO_KEEPALIVE, true);

                ChannelFuture f = b.bind(port).sync();
                f.channel().closeFuture().sync();

            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        }).start();

    }

    public void stop(){
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        log.info("server stop");

    }

}
