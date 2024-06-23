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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Thread.sleep;

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

    private TcpBase tcpService;
    public TcpBase getTcpService() { return tcpService; }
    public void setTcpService(TcpBase tcpService) { this.tcpService = tcpService; }

    final Map<Long, WaitRequest> requestSessions = new ConcurrentHashMap<>();

    final Map<Long, SessionHandler> sessions = new ConcurrentHashMap<>();
    public Map<Long, SessionHandler> getSessions() { return sessions; }

    private ChannelHandlerContext ctx;
    private final AtomicReference<String> channelId = new AtomicReference<>();
    private final AtomicReference<String> remotePublicAddress = new AtomicReference<>();
    private final AtomicInteger remotePublicPort = new AtomicInteger();

    private final AtomicReference<String> remoteLocalAddress = new AtomicReference<>();
    private final AtomicInteger remoteLocalPort = new AtomicInteger();
    private final AtomicReference<String> remoteClientId = new AtomicReference<>();

    private final AtomicInteger remoteKeyId = new AtomicInteger();
    private final AtomicReference<PublicKey> remotePublicKey = new AtomicReference<>();
    private final AtomicReference<byte[]> remoteAESkey = new AtomicReference<>();
    private final AtomicLong lastClientAlive = new AtomicLong();

    private final AtomicReference<PingStatus> myStatus = new AtomicReference<>( PingStatus.ONINIT );
    private final AtomicReference<PingStatus> remoteStatus = new AtomicReference<>( PingStatus.ONINIT );

    private final AtomicBoolean hasRemoteKey = new AtomicBoolean();

    private ConnectionWorker connectionWorker;
    public ConnectionWorker getConnectionWorker() { return connectionWorker; }

    public AtomicReference<PublicKey> getRemotePublicKey() { return remotePublicKey; }
    public ChannelHandlerContext getCtx() { return ctx; }

    public AtomicReference<String> getRemoteClientId() { return remoteClientId; }
    public String getChannelId() { return channelId.get(); }
    public String getRemotePublicAddress() { return remotePublicAddress.get(); }
    public AtomicReference<String> getRemoteLocalAddress() { return remoteLocalAddress; }
    public AtomicInteger getRemoteLocalPort() { return remoteLocalPort; }

    public boolean isConnected() { return myStatus.get() == PingStatus.CONNECTED; }

    public void setConnectionWorker(ConnectionWorker connectionWorker) {
        this.connectionWorker = connectionWorker;
        this.connectionWorker.setHandler( this );
    }
    private final AtomicBoolean disconnected = new AtomicBoolean( false );
    private final AtomicBoolean doPing = new AtomicBoolean();

    // where is where session is created
//    public SessionHandler openSession(SessionHandler session, int sessionTimeOut) {
//        session.handler = this;
//        sessions.put( session.getSessionId(), session );
//        return session;
//    }

    public SessionHandler openSession(int timeOut) {
        return openSession(tcpService.myId(), timeOut);
    }

    public SessionHandler openSession(String id, int timeOut) {
        long sessionId = id != null ?
                UUID.nameUUIDFromBytes(
                        id.getBytes()
                ).getMostSignificantBits()
                : 1000L;
        if(!sessions.containsKey( sessionId )) {
            sessions.put( sessionId, new SessionHandler(sessionId, timeOut, this) {
                @Override public void onReceive(long requestId, ByteString reply) { }
            } );
        }
        return sessions.get( sessionId );
    }


    public void reply(long sessionId, long requestId, RequestType type, ByteString reply) {
        Message m = Message.newBuilder()
                .setType( MessageType.REPLY )
                .setReply(
                        reply!=null
                                ?
                                Reply.newBuilder()
                                        .setSessionId( sessionId )
                                        .setRequestId( requestId )
                                        .setType( type )
                                        .setReplyMessage( reply )
                                        .build()
                                :
                                Reply.newBuilder()
                                        .setSessionId( sessionId )
                                        .setRequestId( requestId )
                                        .setType( type )
                                        .build()
                )
                .build();
        writeMessage( m );
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

        log.debug( "{} -> Server init ch: {}, addr: {}",
                tcpService.myId(),
                Objects.requireNonNull( ctx ).channel().id().asShortText(),
                getRemoteAddress()
        );

        final ConnectionHandler handler = this;
        getTcpService().getTaskPool().execute( () -> {

            int cnt = 0;
            while (handler.remoteStatus.get().getNumber() < PingStatus.CONNECTED.getNumber() && cnt < MAX_RETRY) {
                sendPing();
                writeMessage( Message.newBuilder()
                        .setType( MessageType.INIT )
                        .setInit( Init.newBuilder()
                                .setClientId( getTcpService().myId() )
                                .setLocalAddr( getTcpService().localAddress() )
                                .setLocalPort( getTcpService().localPort() )
                                .setPublicAddress( remotePublicAddress.get() )
                                .setPublicPort( remotePublicPort.get() )
                                .build()
                        )
                        .build()
                );
                outWork.key.incrementAndGet();
                try {
                    sleep( 1000 );
                } catch (InterruptedException ignored) {
                }
                cnt++;
            }

            cnt = 0;
            while (myStatus.get().getNumber() < PingStatus.CONNECTED.getNumber() && cnt < MAX_RETRY) {
                sendPing();
                try {
                    sleep( 1000 );
                } catch (InterruptedException ignored) {
                }
                cnt++;
            }

            if (handler.remoteStatus.get().getNumber() < PingStatus.CONNECTED.getNumber()) {
                log.warn( "{} -> SERVER MISSING INIT ch: {}, status: {}, addr: {}",
                        getTcpService().myId(),
                        channelId,
                        handler.remoteStatus.get(),
                        getRemoteAddress()
                );
            } else {
                tcpService.connect( this );
                hasRemoteKey.set( true );
            }
        } );
    }

    public final void initServer(String channelId, ChannelHandlerContext ctx) {

        this.ctx = ctx;
        this.channelId.set( channelId );
        setPublicAddress( ctx );

        log.debug( "{} -> Client init ch: {}, addr: {}",
                tcpService.myId(),
                Objects.requireNonNull( ctx ).channel().id().asShortText(),
                getRemoteAddress()
        );

        Thread.yield();
        final ConnectionHandler handler = this;
        getTcpService().getTaskPool().execute( () -> {
            Thread.yield();
            int cnt = 0;
            while (handler.remoteStatus.get().getNumber() < PingStatus.CONNECTED.getNumber() && cnt < MAX_RETRY) {
                sendPing();
                try {
                    sleep( 1000 );
                } catch (InterruptedException ignored) {
                }
                cnt++;
            }

            if (handler.remoteStatus.get().getNumber() < PingStatus.CONNECTED.getNumber()) {
                log.warn( "CLIENT MISSING INIT ch: {}, status: {}, addr: {}",
                        channelId,
                        handler.remoteStatus.get(),
                        getRemoteAddress()
                );
            } else {
                tcpService.connect( this );
                hasRemoteKey.set( true );
            }
        } );
    }

    public boolean isOpen() { return !disconnected.get() && ctx != null && ctx.channel().isOpen(); }
    public boolean bothConnected() {
        return remoteStatus.get() == PingStatus.CONNECTED &&
                myStatus.get() == PingStatus.CONNECTED;
    }

    public synchronized boolean writeMessage(Message m) {

        if (isOpen()) {

            outWork.bytes.addAndGet( m.getSerializedSize() );
            if (m.getSerializedSize() > 0 && m.getSerializedSize() < Integer.MAX_VALUE) {
                ctx.writeAndFlush(
                        Unpooled.wrappedBuffer(
                                intToBytes( m.getSerializedSize() ),
                                m.toByteArray()
                        )
                );
                Thread.yield();
                onMessageOut( m );
                return true;
            } else {
                log.error( "{} -> message size: {} wrong", getTcpService().myId(), m.getSerializedSize() );
            }
        } else {

            log.debug( "{} -> NOT open ch: {}, addr: {}",
                    tcpService.myId(),
                    ctx.channel().id().asShortText(),
                    ctx.channel().remoteAddress().toString()
            );
            tcpService.doDisconnect( this );
        }
        return true;
    }

    public synchronized void readMessage(Message m) {

        lastClientAlive.set( System.currentTimeMillis() );
        Thread.yield();
        try {
            onMessageIn( m );
            startPing( getTcpService().pingPeriod() );
            if (m.hasAesKey() && hasRemoteKey.get()) {
                if (m.getAesKey().getId() == remoteKeyId.get()) {
                    byte[] b = m.getAesKey().getKey().toByteArray();
                    remoteAESkey.set( Encrypt.decrypt( b, getTcpService().rsaKey().getPrivate() ) );
                }
            }
            if (m.getType() == MessageType.PING) {
                inWork.ping.incrementAndGet();
                inWork.bytes.addAndGet( m.getSerializedSize() );
                log.debug( "PING, {} -> ch: {}, Status: {}, remoteAddr: {}",
                        getTcpService().myId(),
                        channelId,
                        m.getPing().getStatus(),
                        getRemoteAddress()
                );
                remoteStatus.set( m.getPing().getStatus() );
                if (remoteStatus.get() == PingStatus.ONINIT) {
                    writeMessage( Message.newBuilder()
                            .setType( MessageType.INIT )
                            .setInit( Init.newBuilder()
                                    .setClientId( getTcpService().myId() )
                                    .setLocalAddr( getTcpService().localAddress() )
                                    .setLocalPort( getTcpService().localPort() )
                                    .setPublicAddress( remotePublicAddress.get() )
                                    .setPublicPort( remotePublicPort.get() )
                                    .build()
                            ).build()
                    );
                    outWork.key.incrementAndGet();
                }
                if (remoteStatus.get() == PingStatus.HASINIT) {
                    writeMessage( Message.newBuilder()
                            .setType( MessageType.PUBLIC_KEY )
                            .setPublicKey( PublicRsaKey.newBuilder()
                                    .setId( getTcpService().rsaKey().getPublic().hashCode() )
                                    .setKey( ByteString.copyFrom( getTcpService().rsaKey().getPublic().getEncoded() ) )
                                    .build()
                            )
                            .build() );
                    outWork.key.incrementAndGet();
                }
            } else if (m.getType() == MessageType.INIT) {

                inWork.key.incrementAndGet();
                inWork.bytes.addAndGet( m.getSerializedSize() );

                if (myStatus.get().getNumber() < PingStatus.HASINIT.getNumber()) {
                    myStatus.set( PingStatus.HASINIT );
                }

                remoteClientId.set( m.getInit().getClientId() );
                remoteLocalAddress.set( m.getInit().getLocalAddr() );
                remoteLocalPort.set( m.getInit().getLocalPort() );

                // setting the client public address
                getTcpService().setPublicAddress( m.getInit().getPublicAddress() );
                getTcpService().setPublicPort( m.getInit().getPublicPort() );

                getTcpService().getActiveClients().put( remoteClientId.get(), this );

                log.debug( "{} -> INIT, ch: {}, remoteLocalAddr {}:{}, remoteAddr: {}",
                        getTcpService().myId(),
                        channelId,
                        m.getInit().getLocalAddr(),
                        m.getInit().getLocalPort(),
                        getRemoteAddress()
                );
                sendPing();
            } else if (m.getType() == MessageType.PUBLIC_KEY) {

                inWork.key.incrementAndGet();
                inWork.bytes.addAndGet( m.getSerializedSize() );

                if (myStatus.get().getNumber() < PingStatus.CONNECTED.getNumber()) {

                    myStatus.set( PingStatus.CONNECTED );

                    PublicKey k = KeyFactory.getInstance( "RSA" ).generatePublic(
                            new X509EncodedKeySpec(
                                    m.getPublicKey().getKey().toByteArray()
                            )
                    );
                    remotePublicKey.set( k );
                    remoteKeyId.set( m.getPublicKey().getId() );

                    log.debug( "{} -> GOT KEY, ch: {}, KEYID: {}, addr: {}",
                            getTcpService().myId(),
                            channelId,
                            remoteKeyId.get(),
                            getRemoteAddress()
                    );
                    myStatus.set( PingStatus.CONNECTED );

                    // mark client worker as connected
                    if (connectionWorker != null) {
                        connectionWorker.connected.set( true );
                    }
                    log.info( "{} -> connected to client: {}", tcpService.myId(), getRemoteClientId() );
                    sendPing();
                    startPing( getTcpService().pingPeriod() );

                    onConnect( remoteClientId.get(), remotePublicAddress.get() );
                }
            } else if (m.getType() == MessageType.DISCONNECT) {

                inWork.key.incrementAndGet();
                inWork.bytes.addAndGet( m.getSerializedSize() );

                myStatus.set( PingStatus.DISCONNECTED );
                getTcpService().getActiveClients().remove( remoteClientId.get() );

                onDisonnect( remoteClientId.get(), remotePublicAddress.get() );
                getTcpService().onDisconnected( this );
                log.info( "{} -> disconnected client: {}", getTcpService().myId(), getRemoteClientId() );
            } else if (m.getType() == MessageType.MESSAGE) {
                inWork.message.incrementAndGet();
                inWork.bytes.addAndGet( m.getSerializedSize() );
                log.debug( "{} -> ch: {}, MESSAGE: {}, addr: {}",
                        getTcpService().myId(),
                        channelId,
                        m.getSubMessage().toStringUtf8(),
                        getRemoteAddress()
                );
            } else if (m.getType() == MessageType.RAW_MESSAGE) {
                inWork.message.incrementAndGet();
                inWork.bytes.addAndGet( m.getSerializedSize() );
                log.debug( "{} -> ch: {}, MESSAGE SIZE: {}, addr: {}",
                        getTcpService().myId(),
                        channelId,
                        m.getSubMessage().size(),
                        getRemoteAddress()
                );
            } else if (m.getType() == MessageType.REQUEST) {

                inWork.request.incrementAndGet();
                inWork.bytes.addAndGet( m.getSerializedSize() );
                log.debug( "{} -> ch: {}, REQUEST: {}, addr: {}",
                        getTcpService().myId(),
                        channelId,
                        m.getSubMessage().toStringUtf8(),
                        getRemoteAddress()
                );

                getTcpService().getTaskPool().execute( () -> {

                    try {

                        if (m.getRequest().getType() == RequestType.HTTP) {

                            Http h = Http.parseFrom( m.getSubMessage() );
                            // check is all ok
                            if (getTcpService().checkAccess(
                                    h.getAccessPath(),
                                    h.getRemoteAddress(),
                                    h.getAccessToken(),
                                    h.getAgent()
                            )) {

                                getTcpService().getTaskPool().execute( () -> {

                                    onRequest(
                                            m.getRequest().getSessionId(),
                                            m.getRequest().getRequestId(),
                                            m.getRequest().getType(),
                                            m.getRequest().getDestination(),
                                            m.getRequest().getRequestMessage()
                                    );

                                } );


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

                            Logon replyMessage;
                            if (logon.getClientId().equals( getTcpService().myId() )) {

                                Optional<String> accessPath = getTcpService().setAccess(
                                        logon.getUserId(),
                                        logon.getPassWord(),
                                        logon.getRemoteAddress(),
                                        logon.getAccessToken(),
                                        logon.getAgent()
                                );


                                if (accessPath.isPresent()) {
                                    replyMessage = Logon.newBuilder()
                                            .setAccessPath( accessPath.get() )
                                            .setStatus( FunctionStatus.OK_LOGON )
                                            .setMessage("successfully logged on")

                                            .build();
                                } else {
                                    replyMessage = Logon.newBuilder()
                                            .setAccessPath( accessPath.get() )
                                            .setStatus( FunctionStatus.REJECTED_LOGON )
                                            .setMessage("can not log on")
                                            .build();
                                }
                                reply(
                                        m.getRequest().getSessionId(),
                                        m.getRequest().getRequestId(),
                                        m.getRequest().getType(),
                                        replyMessage.toByteString()
                                );
                            } else {
                                replyMessage = Logon.newBuilder()
                                        .setStatus( FunctionStatus.SERVICE_REJECT )
                                        .setMessage("unknown clientid: " + logon.getClientId())
                                        .build();
                            }
                            reply(
                                    m.getRequest().getSessionId(),
                                    m.getRequest().getRequestId(),
                                    m.getRequest().getType(),
                                    replyMessage.toByteString()
                            );

                        } else {

                            onRequest(
                                    m.getRequest().getSessionId(),
                                    m.getRequest().getRequestId(),
                                    m.getRequest().getType(),
                                    m.getRequest().getDestination(),
                                    m.getRequest().getRequestMessage()
                            );
                        }

                    } catch (Exception e) {
                        log.warn("{} -> {} Request error: {}", tcpService.myId(),getRemoteClientId(),e);
                    }

                });

            } else if (m.getType() == MessageType.REPLY) {
                inWork.reply.incrementAndGet();
                inWork.bytes.addAndGet( m.getSerializedSize() );
                log.debug( "{} -> ch: {}, REPLY addr: {}",
                        getTcpService().myId(),
                        channelId,
                        getRemoteAddress()
                );
                // handle incoming replies
                if (requestSessions.containsKey( m.getReply().getRequestId() )) {
                    final WaitRequest req = requestSessions.get( m.getReply().getRequestId() );
                    tcpService.getTaskPool().execute( () -> req.sessionHandler.handleReply( req, m.getReply() ) );
                    requestSessions.remove( m.getReply().getRequestId() );
                }
            }

        } catch (Exception e) {
            log.warn( "{} -> id: {} -> prosess exception: {}", getTcpService().myId(), channelId, e.toString() );
        }
    }

    public void disconnectRemote() {

        if (!disconnected.getAndSet( true )) {
            if (remoteClientId.get() != null) {
                getTcpService().getActiveClients().remove( remoteClientId.get() );
                myStatus.set( PingStatus.DISCONNECTED );
                if(ctx != null && ctx.channel().isOpen()) {
                    Message m = Message.newBuilder()
                            .setType( MessageType.DISCONNECT )
                            .setPing( Ping.newBuilder()
                                    .setStatus( myStatus.get() )
                                    .build()
                            )
                            .build();

                    ctx.writeAndFlush( Unpooled.wrappedBuffer(
                                    intToBytes( m.getSerializedSize() ),
                                    m.toByteArray()
                            )
                    );
                    log.debug( "{} -> disconnect remote and close handler: {}, client: {}", getTcpService().myId(), getChannelId(), getRemoteClientId() );
                }
                Thread.yield();
            }
            if (ctx != null) {
                ctx.executor().shutdownGracefully( 10, 100, TimeUnit.MICROSECONDS );
                ctx.close();
                Thread.yield();
            }
        }
    }

    public void disconnect() {
        if (!disconnected.getAndSet( true )) {
            Thread.yield();
            if (remoteClientId.get() != null) {
                getTcpService().getActiveClients().remove( remoteClientId.get() );
            }
            myStatus.set( PingStatus.DISCONNECTED );
            log.debug( "{} -> disconnect and close handler: {}, client: {}", getTcpService().myId(), getChannelId(), getRemoteClientId() );
            if (ctx != null) {
                ctx.executor().shutdown();
                ctx.close();
                Thread.yield();
            }
        }
    }

    public void startPing(int timePeriod) {
        if (isOpen() && bothConnected() && !doPing.getAndSet( true )) {
            ctx.executor().scheduleAtFixedRate( () ->
                            sendPing()
                    , 3, timePeriod, TimeUnit.SECONDS );
        }
    }

    public void sendPing() {
        if (!disconnected.get()) {
            Message m = Message.newBuilder()
                    .setType( MessageType.PING )
                    .setPing( Ping.newBuilder()
                            .setStatus( myStatus.get() )
                            .build()
                    )
                    .build();
            writeMessage( m );
            outWork.ping.incrementAndGet();
        }
    }

    public boolean sendMessage(String message) {
        if (!disconnected.get()) {
            if (bothConnected()) {
                Message m = Message.newBuilder()
                        .setType( MessageType.MESSAGE )
                        .setSubMessage( ByteString.copyFromUtf8( message ) )
                        .build();
                outWork.message.incrementAndGet();
                return writeMessage( m );
            } else {
                log.warn( "{} -> ch: {}, wrong status: {}, addr: {}",
                        getTcpService().myId(),
                        channelId,
                        remoteStatus.get().toString(),
                        getRemoteAddress()
                );
                return false;
            }
        }
        return false;
    }

    public boolean sendRawMessage(byte[] bytes) {

        if (!disconnected.get()) {
            if (bothConnected()) {
                Message m = Message.newBuilder()
                        .setType( MessageType.RAW_MESSAGE )
                        .setSubMessage( ByteString.copyFrom( bytes ) )
                        .build();
                outWork.message.incrementAndGet();
                return writeMessage( m );
            } else {
                log.warn( "{} -> ch: {}, wrong status: {}, addr: {}",
                        getTcpService().myId(),
                        channelId,
                        remoteStatus.get().toString(),
                        getRemoteAddress()
                );
                return false;
            }
        }
        return false;
    }

    public void printWork() {

        log.debug( """
                        \r
                        {} -> ch: {}, addr: {} \r
                        OUT > ping: {}, key: {}, message: {}, request: {}, reply: {}, bytes: {} \r
                        IN > ping: {}, key: {}, message: {}, request: {}, reply: {}, bytes: {}
                        """
                ,
                tcpService.myId(),
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
