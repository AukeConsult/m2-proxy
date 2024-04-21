package m2.proxy.tcp;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import m2.proxy.tcp.handlers.ConnectionHandler;
import m2.proxy.tcp.handlers.MessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import m2.proxy.proto.MessageOuterClass.*;

import java.security.KeyPair;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public abstract class TcpBaseServerBase extends TcpBase {

    private static final Logger log = LoggerFactory.getLogger(TcpBaseServerBase.class);

    private final Map<String, ConnectionHandler> clients = new ConcurrentHashMap<>();
    public Map<String, ConnectionHandler> getClients() { return clients;}

    public TcpBaseServerBase(int serverPort, String localAddress, KeyPair rsaKey) {
        super("SERVER",localAddress,serverPort,localAddress,rsaKey);
        setLocalPort(serverPort);
    }

    @Override
    public final void disConnect(ConnectionHandler handler) {
        log.info("server disconnect ch: {}, addr: {}", handler.getChannelId(),handler.getRemoteAddress());
        handler.close();
        clients.remove(handler.getChannelId());
    }

    @Override
    public final void connect(ConnectionHandler handler) {
        handler.startPing(2);
        handler.getCtx().executor().scheduleAtFixedRate(() ->
                        handler.sendMessage("Hello client: " + System.currentTimeMillis())
                , 2, 10, TimeUnit.SECONDS);
    }

    private static class ServerThread implements Runnable {
        private final TcpBaseServerBase server;
        private final EventLoopGroup bossGroup;
        private final EventLoopGroup workerGroup;
        private final String serverAddr;
        private final int serverPort;
        public ServerThread(final TcpBaseServerBase server) {
            this.server=server;
            this.serverAddr=server.serverAddr;
            this.serverPort=server.serverPort;
            this.bossGroup = new NioEventLoopGroup();
            this.workerGroup = new NioEventLoopGroup();
        }

        public void stop() {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            server.clients.clear();
        }

        @Override
        public void run() {
            log.info("Serverthread started");
            try {
                ServerBootstrap serverBootstrap = new ServerBootstrap();
                serverBootstrap.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ch.pipeline().addFirst(new MessageDecoder(server));
                                ch.pipeline().addLast(new SimpleChannelInboundHandler<Message>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
                                        String channelId = ctx.channel().id().asShortText();
                                        if(server.getClients().containsKey(channelId)) {
                                            server.getClients().get(channelId).prosessMessage(msg);
                                        } else {
                                            log.warn("{} -> client not open, ch: {}, addr: {}",
                                                    server.getClientId(),
                                                    channelId,ctx.channel().remoteAddress().toString());
                                        }
                                    }
                                    @Override
                                    public void channelActive(ChannelHandlerContext ctx) {
                                        final ConnectionHandler handler = server.setConnectionHandler();
                                        handler.setServer(server);
                                        handler.initServer(ctx.channel().id().asShortText(),ctx);
                                        server.getClients().put(handler.getChannelId(),handler);
                                    }
                                });
                            }
                        })
                        .option(ChannelOption.SO_BACKLOG, 128)
                        .childOption(ChannelOption.SO_KEEPALIVE, true);

                ChannelFuture f = serverBootstrap.bind(serverAddr,serverPort).sync();
                f.channel().closeFuture().sync();
                f.channel().close();

                log.info("Netty server stopped LOOP");

            } catch (InterruptedException e) {
                log.info("stopped",e);
                //throw new RuntimeException(e);
            }
            log.info("Serverthread stopped");

        }
    }

    private ServerThread serverThread;
    private void startServer () {
        if(serverThread!=null) {
            serverThread.stop();
        }
        serverThread = new ServerThread(this);
        getExecutor().execute(serverThread);
    }

    private void stopServer () {
        if(serverThread!=null) {
            serverThread.stop();
        }
    }

    @Override
    public final void onStart() {
        log.info("{} -> Starting netty server on -> {}:{} ", getClientId(), getLocalAddress(),getLocalPort());
        startServer ();
    }

    @Override
    public final void onStop() {
        stopServer();
        getClients().values().forEach(ConnectionHandler::printWork);
        log.info("{} -> Netty server stopped",getClientId());
    }

    @Override
    final protected void execute() {
        while(isRunning()) {
            waitfor(10000);
            getClients().values().forEach(ConnectionHandler::printWork);
        }
    }

}
