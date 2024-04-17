package m2.proxy;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.m2.MessageOuterClass;

import java.security.KeyPair;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class NettyServer extends Netty {

    private static final Logger log = LoggerFactory.getLogger(NettyServer.class);

    private final Map<ChannelId, Handler> clients = new ConcurrentHashMap<>();
    public Map<ChannelId, Handler> getClients() { return clients;}

    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;

    public NettyServer(int serverPort, String localAddress, KeyPair rsaKey) {
        super("SERVER","",serverPort,localAddress,rsaKey);

        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();
        setLocalPort(serverPort);

    }

    @Override
    public void onStart() {

        log.info("Starting netty server on -> {}:{} ", getLocalAddress(),getLocalPort());
        getExecutor().execute(() -> {

            try {
                final Netty server=this;
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ch.pipeline().addFirst(new BigMessageDecoder(server));
                                ch.pipeline().addLast(new SimpleChannelInboundHandler<MessageOuterClass.Message>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, MessageOuterClass.Message msg) {
                                        clients.get(ctx.channel().id()).prosessMessage(msg);
                                    }
                                    @Override
                                    public void channelActive(ChannelHandlerContext ctx) {
                                        clients.put(ctx.channel().id(),new Handler(server,ctx.channel().id(),ctx));
                                        clients.get(ctx.channel().id()).sendPublicKey();
                                        ctx.executor().scheduleAtFixedRate(() ->
                                                clients.get(ctx.channel().id()).sendMessage("Hello client: " + System.currentTimeMillis()), 0, 2, TimeUnit.SECONDS);
                                    }
                                });
                            }
                        })
                        .option(ChannelOption.SO_BACKLOG, 128)
                        .childOption(ChannelOption.SO_KEEPALIVE, true);

                ChannelFuture f = b.bind(serverAddr,serverPort).sync();
                f.channel().closeFuture().sync();

            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void onStop() {
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        getClients().values().forEach(Handler::printWork);
        log.info("Netty server stopped");
    }

    @Override
    final protected void execute() {
        while(isRunning()) {
            waitfor(10000);
            getClients().values().forEach(Handler::printWork);
        }
    }

}
