package m2.proxy.tcp.handlers;

import com.google.protobuf.ByteString;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import m2.proxy.common.ProxyStatus;
import m2.proxy.common.TcpException;
import m2.proxy.tcp.Encrypt;
import m2.proxy.tcp.TcpBase;

import m2.proxy.proto.MessageOuterClass.*;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ConnectionHandler {
    private static final Logger log = LoggerFactory.getLogger(ConnectionHandler.class);

    private static final int MAX_RETRY = 10;

    public abstract static class ClientWorker implements Runnable {
        public AtomicBoolean running = new AtomicBoolean();
        public abstract void stopWorker();
    }

    public static class WorkCount {
        public AtomicInteger ping = new AtomicInteger();
        public AtomicInteger key = new AtomicInteger();
        public AtomicInteger message = new AtomicInteger();
        public AtomicInteger request = new AtomicInteger();
        public AtomicInteger reply = new AtomicInteger();
        public AtomicInteger bytes = new AtomicInteger();
    }



    public static class WaitRequest {
        boolean async;
        long requestId = TcpBase.rnd.nextLong(Long.MAX_VALUE);
        SessionHandler sessionHandler;
        Request request;
    }

    public WorkCount outWork = new WorkCount();
    public WorkCount inWork = new WorkCount();

    private TcpBase server;
    public TcpBase getServer() {return server;}
    public void setServer(TcpBase server) { this.server = server; }

    final Map<Long, WaitRequest> requestSessions = new ConcurrentHashMap<>();

    final Map<Long, SessionHandler> sessions = new ConcurrentHashMap<>();
    public Map<Long, SessionHandler> getSessions() {
        return sessions;
    }


    private ChannelHandlerContext ctx;
    private final AtomicReference<String> channelId=new AtomicReference<>();
    private final AtomicReference<String> remoteAddress=new AtomicReference<>();

    public ChannelHandlerContext getCtx() { return ctx; }
    public String getChannelId() { return channelId.get(); }
    public String getRemoteAddress() { return remoteAddress.get(); }

    private final AtomicReference<String> remoteLocalAddress = new AtomicReference<>();
    private final AtomicInteger remoteLocalPort = new AtomicInteger();
    private final AtomicReference<String> remoteClientId = new AtomicReference<>();

    private final AtomicInteger remoteKeyId = new AtomicInteger();
    private final AtomicReference<PublicKey> remotePublicKey = new AtomicReference<>();
    private final AtomicReference<byte[]> remoteAESkey = new AtomicReference<>();
    private final AtomicLong lastClientAlive= new AtomicLong();

    private final AtomicReference<PingStatus> myStatus = new AtomicReference<>(PingStatus.ONINIT);
    private final AtomicReference<PingStatus> remoteStatus = new AtomicReference<>(PingStatus.ONINIT);

    private final AtomicBoolean hasRemoteKey = new AtomicBoolean();

    private ClientWorker workerClient;
    public ClientWorker getWorkerClient() { return workerClient;}
    public void setWorkerClient(ClientWorker workerClient) { this.workerClient = workerClient;}

    public void close() {
        if(ctx!=null) {
            ctx.executor().shutdownNow();
            ctx.close();
        }
    }

    private AtomicBoolean doPing = new AtomicBoolean();
    public void startPing(int timePeriod) {
        if(!doPing.getAndSet(true)) {
            ctx.executor().scheduleAtFixedRate(() ->
                            sendPing()
                    , 0, timePeriod, TimeUnit.SECONDS);
        }
    }

    // where is where session is created
    public SessionHandler openSession(SessionHandler session, int sessionTimeOut) throws TcpException {

        long time=0;
        while(!this.isOpen() && time<10000) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
            }
            time+=100;
        }

        if(this.isOpen()) {
            session.handler=this;
            session.sessionTimeOut.set(System.currentTimeMillis()+sessionTimeOut);
            sessions.put(session.getSessionId(),session);
            return session;
        } else {
            throw new TcpException(ProxyStatus.NOTOPEN,"");
        }
    }

    public void reply(long sessionId, long requestId, RequestType type, ByteString reply) {
        Message m = Message.newBuilder()
                .setType(MessageType.REPLY)
                .setReply(
                        Reply.newBuilder()
                        .setSessionId(sessionId)
                        .setRequestId(requestId)
                        .setType(type)
                        .setReplyMessage(reply)
                        .build()
                )
                .build();
        write(m);
    }

    public void write(Message m) {
        if(isOpen()) {
            onMessageOut(m);
            outWork.bytes.addAndGet(m.getSerializedSize());
            ctx.writeAndFlush(Unpooled.wrappedBuffer(
                            intToBytes(MessageDecoder.MESSAGE_ID),
                            intToBytes(m.getSerializedSize()),
                            m.toByteArray()
                    )
            );
        } else {
            log.info("{} -> NOT open ch: {}, addr: {}",
                    server.getClientId(),
                    ctx.channel().id().asShortText(),
                    ctx.channel().remoteAddress().toString()
            );

            server.disConnect(this);
        }
    }

    public ConnectionHandler() {}

    public final void initClient(String channelId, ChannelHandlerContext ctx) {

        this.ctx=ctx;
        this.channelId.set(channelId);
        this.remoteAddress.set(ctx!=null?ctx.channel().remoteAddress().toString():null);

        log.info("{} -> Server init ch: {}, addr: {}",
                server.getClientId(),
                ctx.channel().id().asShortText(),
                ctx.channel().remoteAddress().toString()
        );

        final ConnectionHandler handler = this;
        getServer().getExecutor().execute(()-> {

            int cnt=0;
            while(handler.remoteStatus.get().getNumber() < PingStatus.HASKEY.getNumber() && cnt < MAX_RETRY) {
                sendPing();
                write(Message.newBuilder()
                        .setType(MessageType.INIT)
                        .setInit(Init.newBuilder()
                                .setClientId(getServer().getClientId())
                                .setLocalAddr(getServer().getLocalAddress())
                                .setLocalPort(getServer().getLocalPort())
                                .build()
                        )
                        .setPublicKey(PublicRsaKey.newBuilder()
                                .setId(getServer().getRsaKey().getPublic().hashCode())
                                .setKey(ByteString.copyFrom(getServer().getRsaKey().getPublic().getEncoded()))
                                .build()
                        )
                        .build()
                );
                outWork.key.incrementAndGet();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
                cnt++;
            }

            cnt=0;
            while(myStatus.get().getNumber() < PingStatus.HASKEY.getNumber() && cnt < MAX_RETRY) {
                sendPing();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                }
                cnt++;
            }

            if(handler.remoteStatus.get().getNumber() < PingStatus.HASKEY.getNumber()) {
                log.warn("SERVER MISSING INIT ch: {}, status: {}, addr: {}",
                        channelId,
                        handler.remoteStatus.get(),
                        remoteAddress
                );
            } else {
                server.connect(this);
                hasRemoteKey.set(true);
            }
        });
    }

    public final void initServer(String channelId, ChannelHandlerContext ctx) {

        this.ctx=ctx;
        this.channelId.set(channelId);
        this.remoteAddress.set(ctx!=null?ctx.channel().remoteAddress().toString():null);

        log.info("{} -> Client init ch: {}, addr: {}",
                server.getClientId(),
                ctx.channel().id().asShortText(),
                ctx.channel().remoteAddress().toString()
        );

        final ConnectionHandler handler = this;
        getServer().getExecutor().execute(()-> {
            int cnt=0;
            while(handler.remoteStatus.get().getNumber() < PingStatus.HASKEY.getNumber() && cnt < MAX_RETRY) {
                sendPing();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                }
                cnt++;
            }

            if(handler.remoteStatus.get().getNumber() < PingStatus.HASKEY.getNumber()) {
                log.warn("CLIENT MISSING INIT ch: {}, status: {}, addr: {}",
                        channelId,
                        handler.remoteStatus.get(),
                        remoteAddress
                );
            } else {
                server.connect(this);
                hasRemoteKey.set(true);
            }
        });
    }

    public boolean isOpen() {
        return ctx!=null && ctx.channel().isOpen();
    }
    public void prosessMessage(Message m)  {

        lastClientAlive.set(System.currentTimeMillis());
        onMessageIn(m);
        try {

            if(m.hasAesKey() && hasRemoteKey.get()) {
                if(m.getAesKey().getId() == remoteKeyId.get()) {
                    byte[] b = m.getAesKey().getKey().toByteArray();
                    remoteAESkey.set(Encrypt.decrypt(b,getServer().getRsaKey().getPrivate()));
                }
            }
            if(m.getType()== MessageType.PING) {
                inWork.ping.incrementAndGet();
                inWork.bytes.addAndGet(m.getSerializedSize());
                log.debug("PING, {} -> ch: {}, Status: {}, remoteAddr: {}",
                        getServer().getClientId(),
                        channelId,
                        m.getPing().getStatus(),
                        remoteAddress
                );
                remoteStatus.set(m.getPing().getStatus());
                if(remoteStatus.get()==PingStatus.ONINIT) {
                    write(Message.newBuilder()
                            .setType(MessageType.INIT)
                            .setInit(Init.newBuilder()
                                    .setClientId(getServer().getClientId())
                                    .setLocalAddr(getServer().getLocalAddress())
                                    .setLocalPort(getServer().getLocalPort())
                                    .build()
                            )
                            .setPublicKey(PublicRsaKey.newBuilder()
                                    .setId(getServer().getRsaKey().getPublic().hashCode())
                                    .setKey(ByteString.copyFrom(getServer().getRsaKey().getPublic().getEncoded()))
                                    .build()
                            )
                            .build()
                    );
                    outWork.key.incrementAndGet();
                }
                if(remoteStatus.get()==PingStatus.HASINIT) {
                    write(Message.newBuilder()
                            .setType(MessageType.PUBLIC_KEY)
                            .setPublicKey(PublicRsaKey.newBuilder()
                                    .setId(getServer().getRsaKey().getPublic().hashCode())
                                    .setKey(ByteString.copyFrom(getServer().getRsaKey().getPublic().getEncoded()))
                                    .build()
                            )
                            .build());
                    outWork.key.incrementAndGet();
                }
            } else if(m.getType()== MessageType.INIT) {

                inWork.key.incrementAndGet();
                inWork.bytes.addAndGet(m.getSerializedSize());

                myStatus.set(PingStatus.HASINIT);
                remoteClientId.set(m.getInit().getClientId());
                remoteLocalAddress.set(m.getInit().getLocalAddr());
                remoteLocalPort.set(m.getInit().getLocalPort());

                getServer().getActiveClients().put(remoteClientId.get(),this);

                log.info("{} -> INIT, ch: {}, remoteLocalAddr {}:{}, remoteAddr: {}",
                        getServer().getClientId(),
                        channelId,
                        m.getInit().getLocalAddr(),
                        m.getInit().getLocalPort(),
                        remoteAddress
                );

                if(m.getPublicKey().isInitialized()) {

                    PublicKey k = KeyFactory.getInstance("RSA").generatePublic(
                            new X509EncodedKeySpec(
                                    m.getPublicKey().getKey().toByteArray()
                            )
                    );
                    remotePublicKey.set(k);
                    remoteKeyId.set(m.getPublicKey().getId());
                    if(myStatus.get()==PingStatus.HASINIT) {
                        myStatus.set(PingStatus.HASKEY);
                    }
                    log.info("{} -> GOT KEY, ch: {}, KEYID: {}, addr: {}",
                            getServer().getClientId(),
                            channelId,
                            remoteKeyId.get(),
                            remoteAddress
                    );
                    myStatus.set(PingStatus.HASKEY);
                    onConnect(remoteClientId.get(),remoteAddress.get());

                }
                sendPing();

            } else if(m.getType()== MessageType.PUBLIC_KEY) {

                inWork.key.incrementAndGet();
                inWork.bytes.addAndGet(m.getSerializedSize());

                if(myStatus.get().getNumber()<PingStatus.HASKEY.getNumber()) {

                    PublicKey k = KeyFactory.getInstance("RSA").generatePublic(
                            new X509EncodedKeySpec(
                                    m.getPublicKey().getKey().toByteArray()
                            )
                    );
                    remotePublicKey.set(k);
                    remoteKeyId.set(m.getPublicKey().getId());
                    if(myStatus.get()==PingStatus.HASINIT) {
                        myStatus.set(PingStatus.HASKEY);
                    }
                    log.info("{} -> GOT KEY, ch: {}, KEYID: {}, addr: {}",
                            getServer().getClientId(),
                            channelId,
                            remoteKeyId.get(),
                            remoteAddress
                    );
                    myStatus.set(PingStatus.HASKEY);
                    onConnect(remoteClientId.get(),remoteAddress.get());
                    sendPing();
                }

            } else if(m.getType()== MessageType.MESSAGE) {
                inWork.message.incrementAndGet();
                inWork.bytes.addAndGet(m.getSerializedSize());
                log.info("{} -> ch: {}, MESSAGE: {}, addr: {}",
                        getServer().getClientId(),
                        channelId,
                        m.getSubMessage().toStringUtf8(),
                        remoteAddress
                );
            } else if(m.getType()== MessageType.RAW_MESSAGE) {
                inWork.message.incrementAndGet();
                inWork.bytes.addAndGet(m.getSerializedSize());
                log.debug("{} -> ch: {}, MESSAGE SIZE: {}, addr: {}",
                        getServer().getClientId(),
                        channelId,
                        m.getSubMessage().size(),
                        remoteAddress
                );
            } else if(m.getType()== MessageType.REQUEST) {
                inWork.request.incrementAndGet();
                inWork.bytes.addAndGet(m.getSerializedSize());
                log.debug("{} -> ch: {}, REQUEST: {}, addr: {}",
                        getServer().getClientId(),
                        channelId,
                        m.getSubMessage().toStringUtf8(),
                        remoteAddress
                );
                onRequest(
                        m.getRequest().getSessionId(),
                        m.getRequest().getRequestId(),
                        m.getRequest().getType(),
                        m.getRequest().getDestination(),
                        m.getRequest().getRequestMessage()
                );
            } else if(m.getType()== MessageType.REPLY) {
                inWork.reply.incrementAndGet();
                inWork.bytes.addAndGet(m.getSerializedSize());
                log.debug("{} -> ch: {}, REPLY addr: {}",
                        getServer().getClientId(),
                        channelId,
                        remoteAddress
                );
                // handle incoming replies
                if(requestSessions.containsKey(m.getReply().getRequestId())) {
                    final WaitRequest req = requestSessions.get(m.getReply().getRequestId());
                    server.getTaskPool().execute(() -> req.sessionHandler.handleReply(req,m.getReply()));
                    requestSessions.remove(m.getReply().getRequestId());
                }
            }

        } catch(Exception e) {
            log.warn("{} -> id: {} -> prosess exception: {}",getServer().getClientId(), channelId, e.toString());
        }

    }

    public void sendPing() {

        Message m = Message.newBuilder()
                .setType(MessageType.PING)
                .setPing(Ping.newBuilder()
                        .setStatus(myStatus.get())
                        .build()
                )
                .build();

        write(m);
        outWork.ping.incrementAndGet();

    }
    public void sendMessage(String message) {

        if(hasRemoteKey.get()) {
            Message m = Message.newBuilder()
                    .setType(MessageType.MESSAGE)
                    .setSubMessage(ByteString.copyFromUtf8(message))
                    .build();
            write(m);
            outWork.message.incrementAndGet();

        } else {
            log.warn("{} -> ch: {}, NO KEY REPLY addr: {}",
                    getServer().getClientId(),
                    channelId,
                    remoteAddress
            );
        }

    }

    public void sendRawMessage(byte[] bytes) {

        if(hasRemoteKey.get()) {
            Message m = Message.newBuilder()
                    .setType(MessageType.RAW_MESSAGE)
                    .setSubMessage(ByteString.copyFrom(bytes))
                    .build();
            write(m);
            outWork.message.incrementAndGet();

        } else {
            log.warn("{} -> ch: {}, NO KEY REPLY addr: {}",
                    getServer().getClientId(),
                    channelId,
                    remoteAddress
            );
        }

    }

    public void printWork() {

        log.info("{} -> ch: {}, addr: {} \r\n" +
                        "OUT > ping: {}, key: {}, message: {}, request: {}, reply: {}, bytes: {} \r\n" +
                        "IN > ping: {}, key: {}, message: {}, request: {}, reply: {}, bytes: {} \n\n"
                ,
                server.getClientId(),
                channelId,
                remoteAddress,
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
                (byte) (i >>> 24),
                (byte) (i >>> 16),
                (byte) (i >>> 8),
                (byte) i
        };
    }
    protected abstract void onMessageIn(Message m);
    protected abstract void onMessageOut(Message m);
    protected abstract void onConnect(String ClientId, String remoteAddress);
    protected abstract void onDisconnect(String ClientId);
    protected abstract void onRequest(long sessionId, long requestId, RequestType type, String address, ByteString request);

}
