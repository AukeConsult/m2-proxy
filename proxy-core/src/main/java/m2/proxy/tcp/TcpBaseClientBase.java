package m2.proxy.tcp;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import m2.proxy.tcp.handlers.ConnectionHandler;
import m2.proxy.tcp.handlers.MessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import m2.proxy.proto.MessageOuterClass.Message;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public abstract class TcpBaseClientBase extends TcpBase {
    private static final Logger log = LoggerFactory.getLogger(TcpBaseClientBase.class);
    private static final long RESTART_WAIT = 2000;

    private final Map<String, ClientThread> clients = new ConcurrentHashMap<>();
    public Map<String, ClientThread> getClients() { return clients;}

    public TcpBaseClientBase(String clientId, String serverAddr, int serverPort, String localAddress) {
        super(
                clientId==null?UUID.randomUUID().toString().substring(0,5):clientId,
                serverAddr, serverPort, localAddress, null
        );
        setLocalPort(0);
    }

    @Override
    public final void disConnect(ConnectionHandler handler) {

        getExecutor().execute(() -> {
            try {
                handler.getWorkerClient().stopWorker();
                Thread.sleep(RESTART_WAIT);
                String key = this.serverAddr+this.serverPort;
                getClients().remove(key);

                log.info("{} -> Client reconnect: {}:{}",getClientId(),this.serverAddr,this.serverPort);
                ClientThread clientThread = new ClientThread(this,this.serverAddr,this.serverPort);
                clientThread.running.set(true);
                getExecutor().execute(clientThread);
                getClients().put(key,clientThread);

            } catch (InterruptedException ignored) {
            }

        });
    }

    @Override
    public final void connect(ConnectionHandler handler) {
        handler.startPing(10);
        handler.getCtx().executor().scheduleAtFixedRate(() ->
                        handler.sendMessage("Hello server: " + System.currentTimeMillis())
                , 2, 10, TimeUnit.SECONDS);
    }

    public static class ClientThread extends ConnectionHandler.ClientWorker {

        private final TcpBaseClientBase server;
        private final EventLoopGroup bossGroup;
        private final String serverAddr;
        private final int serverPort;

        private final AtomicReference<ConnectionHandler> connectionHandler = new AtomicReference<>();

        public ConnectionHandler getHandler() {
            if(connectionHandler.get()==null) {
                connectionHandler.set(server.setConnectionHandler());
                connectionHandler.get().setServer(server);
                connectionHandler.get().setWorkerClient(this);
            }
            return connectionHandler.get();
        }

        public ClientThread(final TcpBaseClientBase server, String serverAddr, int serverPort) {
            this.server=server;
            this.serverAddr=serverAddr;
            this.serverPort=serverPort;
            this.bossGroup = new NioEventLoopGroup();
        }

        @Override
        public void stopWorker() {
            if(running.getAndSet(false)) {
                log.debug("{} -> Client disconnect",server.getClientId());
                bossGroup.shutdownGracefully();
                if(connectionHandler.get()!=null) {
                    connectionHandler.get().close();
                }
            }
        }

        @Override
        public void run() {
            log.debug("{} -> Client thread started",server.getClientId());
            try {
                Bootstrap bootstrap = new Bootstrap();
                bootstrap.group(bossGroup)
                        .channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ch.pipeline().addFirst(new MessageDecoder(server));
                                ch.pipeline().addLast(new SimpleChannelInboundHandler<Message>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, Message msg)  {
                                        getHandler().prosessMessage(msg);
                                    }
                                    @Override
                                    public void channelActive(ChannelHandlerContext ctx) {
                                        log.info("open handler");
                                        getHandler().initClient(ctx.channel().id().asShortText(),ctx);

                                    }
                                });
                            }
                        });

                ChannelFuture f = bootstrap.connect(serverAddr, serverPort).sync();
                f.channel().closeFuture().sync();
            } catch (InterruptedException e) {
                log.warn("{} -> Interupt error: {}",server.getClientId(),e.getMessage());
                server.disConnect(getHandler());
            } catch (Exception e) {
                if(e.getCause()!=null) {
                    log.warn("{} -> Connect error: {}",server.getClientId(), e.getCause().getMessage());
                }
                server.disConnect(getHandler());
            }
            log.debug("{} -> Client thread stopped",server.getClientId());
        }

    }

    private void startServers () {
        String key = this.serverAddr+this.serverPort;
        if(getClients().containsKey(key)) {
            if(!getClients().get(key).running.getAndSet(true)) {
                getClients().get(key).stopWorker();
                getClients().remove(key);
            }
        }
        ClientThread clientThread = new ClientThread(this,this.serverAddr,this.serverPort);
        clientThread.running.set(true);
        getExecutor().execute(clientThread);
        getClients().put(key,clientThread);

    }

    @Override
    public void onStart() {
        log.info("Netty client start on {}:{}, connect to host -> {}:{}",
                getLocalAddress(), getLocalPort(), serverAddr, serverPort);
        startServers();
    }

    @Override
    public void onStop() {
        getClients().values().forEach(s -> {
            s.getHandler().printWork();
            if(s.running.get()) {
                s.stopWorker();
                log.info("{} -> client stop: {}:{}",
                        s.server.getClientId(),
                        s.serverAddr,s.serverPort
                );
            }
        });
        getClients().clear();
    }

    @Override
    final protected void execute() {
        while(isRunning()) {
            waitfor(10000);
            getClients().values().forEach(s -> {
                s.getHandler().printWork();
            });
        }
    }
}
