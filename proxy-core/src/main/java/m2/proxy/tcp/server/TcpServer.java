package m2.proxy.tcp.server;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import m2.proxy.common.ProxyStatus;
import m2.proxy.common.TcpException;
import m2.proxy.proto.MessageOuterClass;
import m2.proxy.proto.MessageOuterClass.Message;
import m2.proxy.server.RemoteAccess;
import m2.proxy.tcp.TcpBase;
import m2.proxy.tcp.handlers.ConnectionHandler;
import m2.proxy.tcp.handlers.MessageDecoder;
import m2.proxy.tcp.handlers.SessionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyPair;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class TcpServer extends TcpBase {

    private static final Logger log = LoggerFactory.getLogger( TcpServer.class);

    private final Map<String, ConnectionHandler> clientHandles = new ConcurrentHashMap<>();
    public Map<String, ConnectionHandler> getClientHandles() { return clientHandles;}

    private final Map<String, RemoteAccess> access = new ConcurrentHashMap<>();
    public Map<String, RemoteAccess> getAccess() {
        return access;
    }

    public TcpServer(int serverPort, String localAddress, KeyPair rsaKey) {
        super("SERVER",localAddress,serverPort,localAddress,rsaKey);
        setLocalPort(serverPort);
    }

    protected Optional<SessionHandler> getSession(String clientId, String remoteAddress) {

        if (getActiveClients().containsKey( clientId ) && getActiveClients().get( clientId ).isOpen()) {
            ConnectionHandler client = getActiveClients().get( clientId );
            long sessionId = remoteAddress!=null ?
                    UUID.nameUUIDFromBytes(
                            remoteAddress.getBytes()
                    ).getMostSignificantBits()
                    : 1000L;
            if (!client.getSessions().containsKey( sessionId )) {
                client.openSession( new SessionHandler( sessionId ) {
                    @Override
                    public void onReceive(long requestId, ByteString reply) { }
                }, 10000 );
            }
            return Optional.of( client.getSessions().get( sessionId ) );
        } else {
            return Optional.empty();
        }
    }

    public Optional<String> logon(
            String remoteClient,
            String remoteAddress,
            String userId,
            String passWord,
            String accessToken,
            String agent) throws TcpException {
        try {

            Optional<SessionHandler> session = getSession( remoteClient, remoteAddress );
            if (session.isPresent()) {

                Optional<String> accessPath = session.get().logon(
                        userId,
                        passWord,
                        remoteAddress,
                        accessToken,
                        agent );

                if (accessPath.isPresent()) {
                    log.info( "Got accessPath: {}, client: {}", accessPath.get(), remoteClient );
                    RemoteAccess a = new RemoteAccess( accessPath.get(), getMyId() );
                    access.put( a.getAccessPath(), a );
                    return accessPath;
                } else {
                    log.warn( "Rejected client: {}", remoteClient );
                    throw new TcpException( ProxyStatus.REJECTED, "cant fond client" );
                }
            } else {
                log.warn( "{} -> Can not find client: {}", getMyId(), remoteClient );
                throw new TcpException( ProxyStatus.NOTFOUND, "cant fond client" );
            }

        } catch (TcpException e) {
            throw new TcpException(e.getStatus(),e.getMessage());
        }
    }

    protected Optional<ByteString> forwardHttp(
            String accessPath,
            String path,
            String remoteAddress,
            String accessToken,
            String agent,
            String request,
            int timeOut
    ) throws TcpException {

        String remoteClientId = getAccess().getOrDefault(
                accessPath,
                new RemoteAccess( "","" )
        ).getClientId();

        Optional<SessionHandler> session = getSession( remoteClientId, remoteAddress );
        if (session.isPresent()) {

            log.info( "client: {}, Remote Forward {}", remoteClientId, path );

            MessageOuterClass.Http m = MessageOuterClass.Http.newBuilder()
                    .setAccessPath( accessPath )
                    .setRemoteAddress( remoteAddress )
                    .setAccessToken( accessToken )
                    .setAgent( agent )
                    .setPath( path )
                    .setRequest( ByteString.copyFromUtf8( request ) )
                    .build();

            ByteString ret = session.get().sendRequest(
                    "", m.toByteString(), MessageOuterClass.RequestType.HTTP, timeOut
            );

            if (!ret.isEmpty()) {
                try {
                    MessageOuterClass.HttpReply reply = MessageOuterClass.HttpReply.parseFrom( ret );
                    if (reply.getOkLogon()) {
                        log.warn( "GOT REPLY client: {}", remoteClientId );
                        return Optional.of( reply.getReply() ) ;
                    } else {
                        log.warn( "REJECTED REQUEST client: {}", remoteClientId );
                        throw new TcpException( ProxyStatus.REJECTED, "rejected remote request" );
                    }
                } catch (InvalidProtocolBufferException e) {
                    throw new TcpException( ProxyStatus.FAIL, e.getMessage() );
                }
            }
        }
        throw new TcpException( ProxyStatus.REJECTED, "Cant find client: " + remoteClientId );
    }

    @Override
    public final void disConnect(ConnectionHandler handler) {
        log.debug("server disconnect ch: {}, addr: {}", handler.getChannelId(),handler.getRemotePublicAddress());
        handler.close();
        clientHandles.remove(handler.getChannelId());
    }

    @Override
    public final void connect(ConnectionHandler handler) {
        handler.startPing(2);
    }

    @Override protected boolean onCheckAccess(String accessPath, String clientAddress, String accessToken, String agent) {
        return true;
    }
    @Override protected Optional<String> onSetAccess(String userId, String passWord, String clientAddress, String accessToken, String agent) {
        return Optional.empty();
    }

    private static class ServerThread implements Runnable {
        private final TcpServer server;
        private final EventLoopGroup bossGroup;
        private final EventLoopGroup workerGroup;
        private final String serverAddr;
        private final int serverPort;

        public ServerThread(final TcpServer server) {
            this.server=server;
            this.serverAddr=server.myAddress;
            this.serverPort=server.myPort;
            this.bossGroup = new NioEventLoopGroup();
            this.workerGroup = new NioEventLoopGroup();
        }

        public void stop() {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            server.clientHandles.clear();
        }

        @Override
        public void run() {

            try {

                ServerBootstrap serverBootstrap = new ServerBootstrap();
                serverBootstrap.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ch.pipeline().addFirst(new MessageDecoder(server));
                                ch.pipeline().addLast(new SimpleChannelInboundHandler<byte[]>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, byte[] bytes) {
                                        server.getExecutor().execute( () -> {
                                            try {
                                                Message msg = Message.parseFrom( bytes );
                                                String channelId = ctx.channel().id().asShortText();
                                                if(server.getClientHandles().containsKey(channelId)) {
                                                    server.getClientHandles().get(channelId).readMessage(msg);
                                                } else {
                                                    log.warn("{} -> client not open, ch: {}, addr: {}",
                                                            server.getMyId(),
                                                            channelId,ctx.channel().remoteAddress().toString());
                                                }
                                            } catch (InvalidProtocolBufferException e) {
                                                log.error( "error create message from bytes" );
                                            }
                                        });
                                    }
                                    @Override
                                    public void channelActive(ChannelHandlerContext ctx) {
                                        final ConnectionHandler handler = server.setConnectionHandler();
                                        handler.setTcpServe(server);
                                        handler.initServer(ctx.channel().id().asShortText(),ctx);
                                        server.getClientHandles().put(handler.getChannelId(),handler);
                                    }
                                });
                            }
                        })
                        .option(ChannelOption.SO_BACKLOG, 200)
                        .childOption(ChannelOption.SO_KEEPALIVE, true);

                ChannelFuture f = serverBootstrap.bind(serverAddr,serverPort).sync();
                f.channel().closeFuture().sync();
                f.channel().close();

            } catch (InterruptedException e) {
                log.info("Stopp error:",e);
            }

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
        log.info("{} -> Starting netty server on -> {}:{} ", getMyId(), getLocalAddress(),getLocalPort());
        startServer ();
    }

    @Override
    public final void onStop() {
        stopServer();
    }

    @Override
    final protected void execute() {
        while(isRunning()) {
            waitfor(10000);
        }
    }
}
