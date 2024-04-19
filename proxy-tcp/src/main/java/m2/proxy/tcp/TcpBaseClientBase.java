package m2.proxy.tcp;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import m2.proxy.tcp.handlers.ClientHandlerBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.m2.MessageOuterClass.Message;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public abstract class TcpBaseClientBase extends TcpBase {
    private static final Logger log = LoggerFactory.getLogger(TcpBaseClientBase.class);

    private final EventLoopGroup group;

    private ClientHandlerBase clientHandler;
    public ClientHandlerBase getHandler() { return clientHandler;}

    public TcpBaseClientBase(String clientId, String serverAddr, int ServerPort, String localAddress) {
        super(
                clientId==null?UUID.randomUUID().toString().substring(0,5):clientId,
                serverAddr, ServerPort, localAddress, null
        );
        group = new NioEventLoopGroup();
        setLocalPort(0);
    }

    @Override
    public void onStart() {
        log.info("Netty client start on {}:{}, connect to host -> {}:{}",
                getLocalAddress(), getLocalPort(), serverAddr,serverPort);
        getExecutor().execute(() -> {
            final TcpBase server=this;
            try {
                Bootstrap bootstrap = new Bootstrap();
                bootstrap.group(group)
                        .channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ch.pipeline().addFirst(new MessageDecoder(server));
                                ch.pipeline().addLast(new SimpleChannelInboundHandler<Message>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, Message msg)  {
                                        clientHandler.prosessMessage(msg);
                                    }
                                    @Override
                                    public void channelActive(ChannelHandlerContext ctx) {
                                        clientHandler = setClientHandler(ctx.channel().id().asShortText(),ctx);
                                        clientHandler.onInit();
                                        ctx.executor().scheduleAtFixedRate(() ->
                                                clientHandler.sendMessage("Hello server: " + System.currentTimeMillis())
                                                , 0, 2, TimeUnit.SECONDS);
                                    }

                                });
                            }
                        });

                ChannelFuture f = bootstrap.connect(serverAddr, serverPort).sync();
                f.channel().closeFuture().sync();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void onStop() {
        group.shutdownGracefully();
        getHandler().printWork();
        log.info("client stop");
    }

    @Override
    final protected void execute() {
        while(isRunning()) {
            waitfor(10000);
            if(getHandler()!=null) {
                getHandler().printWork();
            }
        }
    }
}
