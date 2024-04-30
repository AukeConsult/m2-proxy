package m2.proxy.tcp.handlers;

import com.google.protobuf.ByteString;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import m2.proxy.proto.MessageOuterClass.*;
import m2.proxy.tcp.Encrypt;
import m2.proxy.tcp.TcpBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public abstract class ConnectionHandler {
    private static final Logger log = LoggerFactory.getLogger( ConnectionHandler.class );

    private static final int MAX_RETRY = 10;



    public static class WaitRequest {
        boolean async;
        long requestId = TcpBase.rnd.nextLong( Long.MAX_VALUE );
        SessionHandler sessionHandler;
        Request request;
    }

    public ConnectionWorkCount outWork = new ConnectionWorkCount();
    public ConnectionWorkCount inWork = new ConnectionWorkCount();

    private TcpBase server;
    public TcpBase getServer() { return server; }
    public void setServer(TcpBase server) { this.server = server; }

    final Map<Long, WaitRequest> requestSessions = new ConcurrentHashMap<>();

    final Map<Long, SessionHandler> sessions = new ConcurrentHashMap<>();
    public Map<Long, SessionHandler> getSessions() {
        return sessions;
    }

    private ChannelHandlerContext ctx;
    private final AtomicReference<String> channelId = new AtomicReference<>();
    private final AtomicReference<String> remotePublicAddress = new AtomicReference<>();
    private final AtomicInteger remotePublicPort = new AtomicInteger();

    private final AtomicReference<String> remoteLocalAddress = new AtomicReference<>();
    private final AtomicInteger remoteLocalPort = new AtomicInteger();
    private final AtomicReference<String> clientId = new AtomicReference<>();

    private final AtomicInteger remoteKeyId = new AtomicInteger();
    private final AtomicReference<PublicKey> remotePublicKey = new AtomicReference<>();
    private final AtomicReference<byte[]> remoteAESkey = new AtomicReference<>();
    private final AtomicLong lastClientAlive = new AtomicLong();

    private final AtomicReference<PingStatus> myStatus = new AtomicReference<>( PingStatus.ONINIT );
    private final AtomicReference<PingStatus> remoteStatus = new AtomicReference<>( PingStatus.ONINIT );

    private final AtomicBoolean hasRemoteKey = new AtomicBoolean();

    private ConnectionWorker connectionWorker;
    public ConnectionWorker getConnectionWorker() { return connectionWorker; }

    public ChannelHandlerContext getCtx() { return ctx; }
    public AtomicReference<String> getClientId() { return clientId; }
    public String getChannelId() { return channelId.get(); }
    public String getRemotePublicAddress() { return remotePublicAddress.get(); }
    public AtomicReference<String> getRemoteLocalAddress() { return remoteLocalAddress; }
    public AtomicInteger getRemoteLocalPort() { return remoteLocalPort; }

    public void setConnectionWorker(ConnectionWorker connectionWorker) {
        this.connectionWorker = connectionWorker;
        this.connectionWorker.setHandler( this );
    }

    public void close() {
        log.info( "{} -> close handler: {}, client: {}", getServer().getClientId(), getChannelId(), getClientId() );
        if (ctx != null) {
            ctx.executor().shutdownNow();
            ctx.close();
        }
    }

    private final AtomicBoolean doPing = new AtomicBoolean();
    public void startPing(int timePeriod) {
        if (!doPing.getAndSet( true )) {
            ctx.executor().scheduleAtFixedRate( () ->
                            sendPing()
                    , 0, timePeriod, TimeUnit.SECONDS );
        }
    }

    // where is where session is created
    public SessionHandler openSession(SessionHandler session, int sessionTimeOut) {

        session.handler = this;
        session.sessionTimeOut.set( System.currentTimeMillis() + sessionTimeOut );
        sessions.put( session.getSessionId(), session );
        return session;
    }

    public void reply(long sessionId, long requestId, RequestType type, ByteString reply) {
        Message m = Message.newBuilder()
                .setType( MessageType.REPLY )
                .setReply(
                        Reply.newBuilder()
                                .setSessionId( sessionId )
                                .setRequestId( requestId )
                                .setType( type )
                                .setReplyMessage( reply )
                                .build()
                )
                .build();
        write( m );
    }

    public void write(Message m) {
        if (isOpen()) {
            onMessageOut( m );
            outWork.bytes.addAndGet( m.getSerializedSize() );
            ctx.writeAndFlush( Unpooled.wrappedBuffer(
                            intToBytes( MessageDecoder.MESSAGE_ID ),
                            intToBytes( m.getSerializedSize() ),
                            m.toByteArray()
                    )
            );
        } else {
            log.info( "{} -> NOT open ch: {}, addr: {}",
                    server.getClientId(),
                    ctx.channel().id().asShortText(),
                    ctx.channel().remoteAddress().toString()
            );
            getServer().getActiveClients().remove( clientId.get() );
            server.disConnect( this );
        }
    }

    public ConnectionHandler() { }

    private String getRemoteAddress() {
        return remotePublicAddress.get() + ":" + remotePublicPort.get();
    }

    private void setPublicAddress(ChannelHandlerContext ctx) {
        if (ctx != null) {
            InetSocketAddress a = ( InetSocketAddress ) ctx.channel().remoteAddress();
            this.remotePublicAddress.set( a.getAddress().getHostAddress() );
            this.remotePublicPort.set( a.getPort() );
        }
    }

    public final void initClient(String channelId, ChannelHandlerContext ctx) {

        this.ctx = ctx;
        this.channelId.set( channelId );
        setPublicAddress( ctx );

        log.info( "{} -> Server init ch: {}, addr: {}",
                server.getClientId(),
                Objects.requireNonNull( ctx ).channel().id().asShortText(),
                ctx.channel().remoteAddress().toString()
        );

        final ConnectionHandler handler = this;
        getServer().getExecutor().execute( () -> {

            int cnt = 0;
            while (handler.remoteStatus.get().getNumber() < PingStatus.HASKEY.getNumber() && cnt < MAX_RETRY) {
                sendPing();
                write( Message.newBuilder()
                        .setType( MessageType.INIT )
                        .setInit( Init.newBuilder()
                                .setClientId( getServer().getClientId() )
                                .setLocalAddr( getServer().getLocalAddress() )
                                .setLocalPort( getServer().getLocalPort() )
                                .setPublicAddress( remotePublicAddress.get() )
                                .setPublicPort( remotePublicPort.get() )
                                .build()
                        )
                        .setPublicKey( PublicRsaKey.newBuilder()
                                .setId( getServer().getRsaKey().getPublic().hashCode() )
                                .setKey( ByteString.copyFrom( getServer().getRsaKey().getPublic().getEncoded() ) )
                                .build()
                        )
                        .build()
                );
                outWork.key.incrementAndGet();
                try {
                    Thread.sleep( 500 );
                } catch (InterruptedException ignored) {
                }
                cnt++;
            }

            cnt = 0;
            while (myStatus.get().getNumber() < PingStatus.HASKEY.getNumber() && cnt < MAX_RETRY) {
                sendPing();
                try {
                    Thread.sleep( 100 );
                } catch (InterruptedException ignored) {
                }
                cnt++;
            }

            if (handler.remoteStatus.get().getNumber() < PingStatus.HASKEY.getNumber()) {
                log.warn( "SERVER MISSING INIT ch: {}, status: {}, addr: {}",
                        channelId,
                        handler.remoteStatus.get(),
                        getRemoteAddress()
                );
            } else {
                server.connect( this );
                hasRemoteKey.set( true );
            }
        } );
    }

    public final void initServer(String channelId, ChannelHandlerContext ctx) {

        this.ctx = ctx;
        this.channelId.set( channelId );
        setPublicAddress( ctx );

        log.info( "{} -> Client init ch: {}, addr: {}",
                server.getClientId(),
                Objects.requireNonNull( ctx ).channel().id().asShortText(),
                ctx.channel().remoteAddress().toString()
        );

        final ConnectionHandler handler = this;
        getServer().getExecutor().execute( () -> {
            int cnt = 0;
            while (handler.remoteStatus.get().getNumber() < PingStatus.HASKEY.getNumber() && cnt < MAX_RETRY) {
                sendPing();
                try {
                    Thread.sleep( 100 );
                } catch (InterruptedException ignored) {
                }
                cnt++;
            }

            if (handler.remoteStatus.get().getNumber() < PingStatus.HASKEY.getNumber()) {
                log.warn( "CLIENT MISSING INIT ch: {}, status: {}, addr: {}",
                        channelId,
                        handler.remoteStatus.get(),
                        getRemoteAddress()
                );
            } else {
                server.connect( this );
                hasRemoteKey.set( true );
            }
        } );
    }

    public boolean isOpen() {
        return ctx != null && ctx.channel().isOpen();
    }
    public void prosessMessage(Message m) {

        lastClientAlive.set( System.currentTimeMillis() );
        onMessageIn( m );
        try {

            if (m.hasAesKey() && hasRemoteKey.get()) {
                if (m.getAesKey().getId() == remoteKeyId.get()) {
                    byte[] b = m.getAesKey().getKey().toByteArray();
                    remoteAESkey.set( Encrypt.decrypt( b, getServer().getRsaKey().getPrivate() ) );
                }
            }
            if (m.getType() == MessageType.PING) {
                inWork.ping.incrementAndGet();
                inWork.bytes.addAndGet( m.getSerializedSize() );
                log.debug( "PING, {} -> ch: {}, Status: {}, remoteAddr: {}",
                        getServer().getClientId(),
                        channelId,
                        m.getPing().getStatus(),
                        getRemoteAddress()
                );
                remoteStatus.set( m.getPing().getStatus() );
                if (remoteStatus.get() == PingStatus.ONINIT) {
                    write( Message.newBuilder()
                            .setType( MessageType.INIT )
                            .setInit( Init.newBuilder()
                                    .setClientId( getServer().getClientId() )
                                    .setLocalAddr( getServer().getLocalAddress() )
                                    .setLocalPort( getServer().getLocalPort() )
                                    .setPublicAddress( remotePublicAddress.get() )
                                    .setPublicPort( remotePublicPort.get() )
                                    .build()
                            )
                            .setPublicKey( PublicRsaKey.newBuilder()
                                    .setId( getServer().getRsaKey().getPublic().hashCode() )
                                    .setKey( ByteString.copyFrom( getServer().getRsaKey().getPublic().getEncoded() ) )
                                    .build()
                            ).build()
                    );
                    outWork.key.incrementAndGet();
                }
                if (remoteStatus.get() == PingStatus.HASINIT) {
                    write( Message.newBuilder()
                            .setType( MessageType.PUBLIC_KEY )
                            .setPublicKey( PublicRsaKey.newBuilder()
                                    .setId( getServer().getRsaKey().getPublic().hashCode() )
                                    .setKey( ByteString.copyFrom( getServer().getRsaKey().getPublic().getEncoded() ) )
                                    .build()
                            )
                            .build() );
                    outWork.key.incrementAndGet();
                }
            } else if (m.getType() == MessageType.INIT) {

                inWork.key.incrementAndGet();
                inWork.bytes.addAndGet( m.getSerializedSize() );

                myStatus.set( PingStatus.HASINIT );
                clientId.set( m.getInit().getClientId() );
                remoteLocalAddress.set( m.getInit().getLocalAddr() );
                remoteLocalPort.set( m.getInit().getLocalPort() );

                // setting the client public address
                getServer().setPublicAddress( m.getInit().getPublicAddress() );
                getServer().setPublicPort( m.getInit().getPublicPort() );

                getServer().getActiveClients().put( clientId.get(), this );

                log.info( "{} -> INIT, ch: {}, remoteLocalAddr {}:{}, remoteAddr: {}",
                        getServer().getClientId(),
                        channelId,
                        m.getInit().getLocalAddr(),
                        m.getInit().getLocalPort(),
                        getRemoteAddress()
                );

                if (m.getPublicKey().isInitialized()) {

                    PublicKey k = KeyFactory.getInstance( "RSA" ).generatePublic(
                            new X509EncodedKeySpec(
                                    m.getPublicKey().getKey().toByteArray()
                            )
                    );
                    remotePublicKey.set( k );
                    remoteKeyId.set( m.getPublicKey().getId() );
                    if (myStatus.get() == PingStatus.HASINIT) {
                        myStatus.set( PingStatus.HASKEY );
                    }
                    log.info( "{} -> GOT KEY, ch: {}, KEYID: {}, addr: {}",
                            getServer().getClientId(),
                            channelId,
                            remoteKeyId.get(),
                            getRemoteAddress()
                    );
                    myStatus.set( PingStatus.HASKEY );
                    onConnect( clientId.get(), remotePublicAddress.get() );
                }
                sendPing();
            } else if (m.getType() == MessageType.DISCONNECT) {

                inWork.key.incrementAndGet();
                inWork.bytes.addAndGet( m.getSerializedSize() );

                log.info( "{} -> ch: {}, DISCONNECT, addr: {}",
                        getServer().getClientId(),
                        channelId,
                        getRemoteAddress()
                );

                myStatus.set( PingStatus.DISCONNECTED );
                getServer().getActiveClients().remove( clientId.get() );

                onDisonnect( clientId.get(), remotePublicAddress.get() );
                getServer().disConnect( this );
            } else if (m.getType() == MessageType.PUBLIC_KEY) {

                inWork.key.incrementAndGet();
                inWork.bytes.addAndGet( m.getSerializedSize() );

                if (myStatus.get().getNumber() < PingStatus.HASKEY.getNumber()) {

                    PublicKey k = KeyFactory.getInstance( "RSA" ).generatePublic(
                            new X509EncodedKeySpec(
                                    m.getPublicKey().getKey().toByteArray()
                            )
                    );
                    remotePublicKey.set( k );
                    remoteKeyId.set( m.getPublicKey().getId() );
                    if (myStatus.get() == PingStatus.HASINIT) {
                        myStatus.set( PingStatus.HASKEY );
                    }
                    log.info( "{} -> GOT KEY, ch: {}, KEYID: {}, addr: {}",
                            getServer().getClientId(),
                            channelId,
                            remoteKeyId.get(),
                            getRemoteAddress()
                    );
                    myStatus.set( PingStatus.HASKEY );
                    onConnect( clientId.get(), remotePublicAddress.get() );
                    sendPing();
                }
            } else if (m.getType() == MessageType.MESSAGE) {
                inWork.message.incrementAndGet();
                inWork.bytes.addAndGet( m.getSerializedSize() );
                log.info( "{} -> ch: {}, MESSAGE: {}, addr: {}",
                        getServer().getClientId(),
                        channelId,
                        m.getSubMessage().toStringUtf8(),
                        getRemoteAddress()
                );
            } else if (m.getType() == MessageType.RAW_MESSAGE) {
                inWork.message.incrementAndGet();
                inWork.bytes.addAndGet( m.getSerializedSize() );
                log.debug( "{} -> ch: {}, MESSAGE SIZE: {}, addr: {}",
                        getServer().getClientId(),
                        channelId,
                        m.getSubMessage().size(),
                        getRemoteAddress()
                );
            } else if (m.getType() == MessageType.REQUEST) {
                inWork.request.incrementAndGet();
                inWork.bytes.addAndGet( m.getSerializedSize() );
                log.debug( "{} -> ch: {}, REQUEST: {}, addr: {}",
                        getServer().getClientId(),
                        channelId,
                        m.getSubMessage().toStringUtf8(),
                        getRemoteAddress()
                );

                if (m.getRequest().getType() == RequestType.HTTP) {

                    Http h = Http.parseFrom( m.getSubMessage() );
                    // check is all ok
                    if (getServer().checkAccess(
                            h.getAccessPath(),
                            h.getRemoteAddress(),
                            h.getAccessToken(),
                            h.getAgent()
                    )) {

                        onRequest(
                                m.getRequest().getSessionId(),
                                m.getRequest().getRequestId(),
                                m.getRequest().getType(),
                                m.getRequest().getDestination(),
                                m.getRequest().getRequestMessage()
                        );
                    } else {

                        reply( m.getRequest().getSessionId(),
                                m.getRequest().getRequestId(), m.getRequest().getType(),
                                HttpReply.newBuilder()
                                        .setOkLogon( false )
                                        .build()
                                        .toByteString()
                        );
                    }
                } else if (m.getRequest().getType() == RequestType.LOGON) {

                    Logon logon = Logon.parseFrom( m.getRequest().getRequestMessage() );
                    if (logon.getClientId().equals( getClientId().get() )) {
                        Optional<String> accessPath = getServer().setAccess(
                                logon.getUserId(),
                                logon.getPassWord(),
                                logon.getRemoteAddress(),
                                logon.getAccessToken(),
                                logon.getAgent()
                        );
                        Logon replyMessage;
                        if (accessPath.isPresent()) {
                            replyMessage = Logon.newBuilder()
                                    .setAccessPath( accessPath.get() )
                                    .setOkLogon( true )
                                    .build();
                        } else {
                            replyMessage = Logon.newBuilder()
                                    .setAccessPath( accessPath.get() )
                                    .setOkLogon( false )
                                    .build();
                        }
                        reply(
                                m.getRequest().getSessionId(),
                                m.getRequest().getRequestId(),
                                m.getRequest().getType(),
                                replyMessage.toByteString()
                        );
                    }
                } else {

                    onRequest(
                            m.getRequest().getSessionId(),
                            m.getRequest().getRequestId(),
                            m.getRequest().getType(),
                            m.getRequest().getDestination(),
                            m.getRequest().getRequestMessage()
                    );
                }
            } else if (m.getType() == MessageType.REPLY) {
                inWork.reply.incrementAndGet();
                inWork.bytes.addAndGet( m.getSerializedSize() );
                log.debug( "{} -> ch: {}, REPLY addr: {}",
                        getServer().getClientId(),
                        channelId,
                        getRemoteAddress()
                );
                // handle incoming replies
                if (requestSessions.containsKey( m.getReply().getRequestId() )) {
                    final WaitRequest req = requestSessions.get( m.getReply().getRequestId() );
                    server.getTaskPool().execute( () -> req.sessionHandler.handleReply( req, m.getReply() ) );
                    requestSessions.remove( m.getReply().getRequestId() );
                }
            }
        } catch (Exception e) {
            log.warn( "{} -> id: {} -> prosess exception: {}", getServer().getClientId(), channelId, e.toString() );
        }
    }

    public void sendPing() {

        Message m = Message.newBuilder()
                .setType( MessageType.PING )
                .setPing( Ping.newBuilder()
                        .setStatus( myStatus.get() )
                        .build()
                )
                .build();

        write( m );
        outWork.ping.incrementAndGet();
    }

    public void sendDisconnect() {

        myStatus.set( PingStatus.DISCONNECTED );
        Message m = Message.newBuilder()
                .setType( MessageType.DISCONNECT )
                .setPing( Ping.newBuilder()
                        .setStatus( myStatus.get() )
                        .build()
                )
                .build();

        write( m );
        outWork.ping.incrementAndGet();
    }

    public void sendMessage(String message) {

        if (hasRemoteKey.get()) {
            Message m = Message.newBuilder()
                    .setType( MessageType.MESSAGE )
                    .setSubMessage( ByteString.copyFromUtf8( message ) )
                    .build();
            write( m );
            outWork.message.incrementAndGet();
        } else {
            log.warn( "{} -> ch: {}, NO KEY REPLY addr: {}",
                    getServer().getClientId(),
                    channelId,
                    getRemoteAddress()
            );
        }
    }

    public void sendRawMessage(byte[] bytes) {

        if (hasRemoteKey.get()) {
            Message m = Message.newBuilder()
                    .setType( MessageType.RAW_MESSAGE )
                    .setSubMessage( ByteString.copyFrom( bytes ) )
                    .build();
            write( m );
            outWork.message.incrementAndGet();
        } else {
            log.warn( "{} -> ch: {}, NO KEY REPLY addr: {}",
                    getServer().getClientId(),
                    channelId,
                    getRemoteAddress()
            );
        }
    }

    public void printWork() {

        log.info( """
                        \r
                        {} -> ch: {}, addr: {} \r
                        OUT > ping: {}, key: {}, message: {}, request: {}, reply: {}, bytes: {} \r
                        IN > ping: {}, key: {}, message: {}, request: {}, reply: {}, bytes: {}
                        """
                ,
                server.getClientId(),
                channelId,
                getRemoteAddress(),
                outWork.ping.get(),
                outWork.key.get(),
                outWork.message.get(),
                outWork.request.get(),
                outWork.reply.get(),
                outWork.bytes.get(),
                inWork.ping.get(),
                inWork.key.get(),
                inWork.message.get(),
                inWork.request.get(),
                inWork.reply.get(),
                inWork.bytes.get()

        );
    }

    byte[] intToBytes(int i) {
        return new byte[]{
                ( byte ) (i >>> 24),
                ( byte ) (i >>> 16),
                ( byte ) (i >>> 8),
                ( byte ) i
        };
    }
    protected abstract void onMessageIn(Message m);
    protected abstract void onMessageOut(Message m);
    protected abstract void onConnect(String ClientId, String remoteAddress);
    protected abstract void onDisonnect(String ClientId, String remoteAddress);
    protected abstract void onRequest(long sessionId, long requestId, RequestType type, String destination, ByteString request);
}
