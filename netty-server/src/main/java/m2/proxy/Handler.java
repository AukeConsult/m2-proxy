package m2.proxy;

import com.google.protobuf.ByteString;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.m2.MessageOuterClass;
import proto.m2.MessageOuterClass.*;

import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class Handler {

    private static final Logger log = LoggerFactory.getLogger(Handler.class);

    public static class WorkCount {
        public AtomicInteger ping = new AtomicInteger();
        public AtomicInteger key = new AtomicInteger();
        public AtomicInteger message = new AtomicInteger();
        public AtomicInteger bytes = new AtomicInteger();
    }

    private final ChannelId channelId;
    private final ChannelHandlerContext ctx;
    private final SocketAddress remoteAddr;
    private final Netty server;

    AtomicReference<String> remoteLocalHost = new AtomicReference<>();
    AtomicInteger remoteLocalPort = new AtomicInteger();
    AtomicReference<String> remoteClientId = new AtomicReference<>();

    AtomicInteger remoteKeyId = new AtomicInteger();
    AtomicBoolean remoteHasKey = new AtomicBoolean(false);
    AtomicReference<PublicKey> remotePublicKey = new AtomicReference<>();
    AtomicReference<byte[]> remoteAESkey = new AtomicReference<byte[]>();
    AtomicLong lastClientAlive= new AtomicLong();

    AtomicBoolean hasRemoteKey = new AtomicBoolean();

    private final HttpHandler httpHandler;

    public WorkCount outWork = new WorkCount();
    public WorkCount inWork = new WorkCount();

    byte[] intToBytes(int i) {
        return new byte[]{
                (byte) (i >>> 24),
                (byte) (i >>> 16),
                (byte) (i >>> 8),
                (byte) i
        };
    }
    private void write(Message m) {

        ctx.writeAndFlush(Unpooled.wrappedBuffer(
                    intToBytes(BigMessageDecoder.MESSAGE_ID),
                    intToBytes(m.getSerializedSize()),
                    m.toByteArray()
                )
        );
        outWork.bytes.addAndGet(m.getSerializedSize());
    }

    public Handler(Netty server, ChannelId channelId, ChannelHandlerContext ctx) {

        this.channelId=channelId;
        this.ctx=ctx;
        this.remoteAddr=ctx.channel().remoteAddress();
        this.server=server;

        this.httpHandler = new HttpHandler(server);

        ctx.executor().scheduleAtFixedRate(() -> {
            sendPing();
        }, 0, 10, TimeUnit.SECONDS);

        log.info("active client ch: {}, addr: {}",
                ctx.channel().id().asShortText(),
                ctx.channel().remoteAddress().toString()
        );

    }

    public void prosessMessage(Message m)  {

        lastClientAlive.set(System.currentTimeMillis());
        try {

            if(m.hasAesKey() && hasRemoteKey.get()) {
                if(m.getAesKey().getId()== remoteKeyId.get()) {
                    byte[] b = m.getAesKey().getKey().toByteArray();
                    remoteAESkey.set(Encrypt.decrypt(b,server.rsaKey.getPrivate()));
                }
            }

            if(m.getType()== MessageType.PING) {

                inWork.ping.incrementAndGet();
                inWork.bytes.addAndGet(m.getSerializedSize());

                log.info("PING, {} -> ch: {}, localAddr {}:{}, addr: {}",
                        server.clientId,
                        channelId.asShortText(),
                        m.getPing().getLocalHost(),
                        m.getPing().getLocalPort(),
                        remoteAddr.toString()
                );

                remoteClientId.set(m.getPing().getClientId());
                remoteLocalHost.set(m.getPing().getLocalHost());
                remoteLocalPort.set(m.getPing().getLocalPort());
                remoteHasKey.set(m.getPing().getHaskey());

                server.activeClients.put(remoteClientId.get(),this);

            } else if(m.getType()== MessageType.PUBLIC_KEY) {

                inWork.key.incrementAndGet();
                inWork.bytes.addAndGet(m.getSerializedSize());

                PublicKey k = KeyFactory.getInstance("RSA").generatePublic(
                        new X509EncodedKeySpec(
                                m.getPublicKey().getKey().toByteArray()
                        )
                );

                remotePublicKey.set(k);
                remoteKeyId.set(m.getPublicKey().getId());
                hasRemoteKey.set(true);

                log.info("GOT KEY, ch: {}, KEYID: {}, addr: {}",
                        channelId.asShortText(),
                        remoteKeyId.get(),
                        remoteAddr.toString()
                );
                sendPing();

            } else if(m.getType()== MessageType.MESSAGE) {
                inWork.message.incrementAndGet();
                inWork.bytes.addAndGet(m.getSerializedSize());

                log.debug("{} -> ch: {}, MESSAGE: {}, addr: {}",
                        server.clientId,
                        channelId.asShortText(),
                        m.getSubMessage().toStringUtf8(),
                        remoteAddr.toString()
                );
            }

        } catch(Exception e) {
            log.warn("{} -> id: {} -> prosess exception: {}",server.clientId, channelId.asShortText(), e.toString());
        }

    }

    public void sendMessage(String msg) {
        if(hasRemoteKey.get()) {
            Message m = Message.newBuilder()
                    .setType(MessageType.MESSAGE)
                    .setSubMessage(ByteString.copyFrom(msg, StandardCharsets.UTF_8))
                    //.setMessage(msg)
                    .build();
            write(m);
            outWork.message.incrementAndGet();
        } else {
            log.info("NO REMOTE KEY ch: {}, Message size: {}, addr: {}",
                    ctx.channel().id().asShortText(),
                    msg.length(),
                    ctx.channel().remoteAddress().toString()
            );
        }
    }

    public void sendPing() {

        Message m = Message.newBuilder()
                .setType(MessageType.PING)
                .setPing(Ping.newBuilder()
                        .setClientId(server.clientId)
                        .setLocalHost(server.localHost)
                        .setLocalPort(server.localPort)
                        .setHaskey(hasRemoteKey.get())
                        .build()
                )
                .build();

        write(m);
        outWork.ping.incrementAndGet();

    }

    public void sendPublicKey() {

        final Handler client = this;
        server.getExecutor().execute(()-> {

            int cnt=0;
            while(!client.remoteHasKey.get() && cnt < 10) {

                Message m = Message.newBuilder()
                        .setType(MessageType.PUBLIC_KEY)
                        .setPublicKey(MessageOuterClass.PublicRsaKey.newBuilder()
                                .setId(server.rsaKey.getPublic().hashCode())
                                .setKey(ByteString.copyFrom(server.rsaKey.getPublic().getEncoded()))
                                .build()
                        )
                        .build();


                write(m);
                outWork.key.incrementAndGet();

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
                cnt++;
            }

            if(!client.remoteHasKey.get()) {
                log.warn("REMOTE MISSING KEY ch: {}, keyid: {}, addr: {}",
                        channelId.asShortText(),
                        server.rsaKey.getPublic().hashCode(),
                        remoteAddr.toString()
                );
            }
        });
    }

    public void printWork() {

        log.info("{} -> ch: {}, addr: {} \r\n" +
                        "OUT > ping: {}, key: {}, message: {}, bytes: {} \r\n" +
                        "IN > ping: {}, key: {}, message: {}, bytes: {} \r\n"
                ,
                server.clientId,
                channelId.asShortText(),
                remoteAddr.toString(),
                outWork.ping.get(),
                outWork.key.get(),
                outWork.message.get(),
                outWork.bytes.get(),
                inWork.ping.get(),
                inWork.key.get(),
                inWork.message.get(),
                inWork.bytes.get()

        );

    }

}
