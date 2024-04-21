package m2.proxy.tcp;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import m2.proxy.tcp.handlers.ClientHandlerBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.m2.MessageOuterClass;

import java.security.KeyPair;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public abstract class TcpBaseServerBase extends TcpBase {

    private static final Logger log = LoggerFactory.getLogger(TcpBaseServerBase.class);

    private final Map<String, ClientHandlerBase> clients = new ConcurrentHashMap<>();
    public Map<String, ClientHandlerBase> getClients() { return clients;}

    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;

    public TcpBaseServerBase(int serverPort, String localAddress, KeyPair rsaKey) {
        super("SERVER",localAddress,serverPort,localAddress,rsaKey);

        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();
        setLocalPort(serverPort);

    }

    @Override
    public final void onStart() {

        log.info("Starting netty server on -> {}:{} ", getLocalAddress(),getLocalPort());
        getExecutor().execute(() -> {

            try {
                final TcpBase server=this;
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ch.pipeline().addFirst(new MessageDecoder(server));
                                ch.pipeline().addLast(new SimpleChannelInboundHandler<MessageOuterClass.Message>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, MessageOuterClass.Message msg) {
                                        clients.get(ctx.channel().id().asShortText()).prosessMessage(msg);
                                    }
                                    @Override
                                    public void channelActive(ChannelHandlerContext ctx) {
                                        setClientHandler(ctx.channel().id().asShortText(),ctx);
                                        ctx.executor().scheduleAtFixedRate(() ->
                                                clients.get(ctx.channel().id().asShortText()).sendMessage("Hello client: " + System.currentTimeMillis()), 0, 2, TimeUnit.SECONDS);
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
    public final void onStop() {
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        getClients().values().forEach(ClientHandlerBase::printWork);
        log.info("Netty server stopped");
    }

    @Override
    final protected void execute() {
        while(isRunning()) {
            waitfor(10000);
            getClients().values().forEach(ClientHandlerBase::printWork);
        }
    }

}
