package m2.proxy;

import io.netty.bootstrap.Bootstrap;
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

    private final EventLoopGroup group;

    private Handler handler;
    public Handler getHandler() { return handler;}

    public NettyClient(String host, int port) {
        super(UUID.randomUUID().toString().substring(0,5), host, port,null);
        group = new NioEventLoopGroup();
    }

    @Override
    protected void onStart() {
        log.info("client start");
        getExecutor().execute(() -> {
            final Netty server=this;
            try {
                Bootstrap b = new Bootstrap();
                b.group(group)
                        .channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ch.pipeline().addFirst(new BigMessageDecoder(server));
                                ch.pipeline().addLast(new SimpleChannelInboundHandler<Message>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, Message msg)  {
                                        handler.prosessMessage(msg);
                                    }
                                    @Override
                                    public void channelActive(ChannelHandlerContext ctx) {
                                        handler = new Handler(server,ctx.channel().id(),ctx);
                                        handler.sendPublicKey();
                                        ctx.executor().scheduleAtFixedRate(() ->
                                                handler.sendMessage("Hello server: " + System.currentTimeMillis())
                                                , 0, 2, TimeUnit.SECONDS);
                                    }

                                });
                            }
                        });

                ChannelFuture f = b.connect(localHost, localPort).sync();
                f.channel().closeFuture().sync();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    protected void onStop() {
        group.shutdownGracefully();
        getHandler().printWork();
        log.info("client stop");
    }

    @Override
    final protected void execute() {
        while(isRunning()) {
            waitfor(10000);
            getHandler().printWork();
        }
    }
}
