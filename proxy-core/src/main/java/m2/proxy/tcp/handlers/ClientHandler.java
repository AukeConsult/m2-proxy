package m2.proxy.tcp.handlers;

import com.google.protobuf.ByteString;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import m2.proxy.tcp.Encrypt;
import m2.proxy.tcp.MessageDecoder;
import m2.proxy.tcp.TcpBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.m2.MessageOuterClass;
import proto.m2.MessageOuterClass.*;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public abstract class ClientHandler extends ClientHandlerBase {

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

    private final ChannelHandlerContext ctx;

    AtomicReference<String> remoteLocalAddress = new AtomicReference<>();
    AtomicInteger remoteLocalPort = new AtomicInteger();
    AtomicReference<String> remoteClientId = new AtomicReference<>();

    AtomicInteger remoteKeyId = new AtomicInteger();
    AtomicReference<PublicKey> remotePublicKey = new AtomicReference<>();
    AtomicReference<byte[]> remoteAESkey = new AtomicReference<>();
    AtomicLong lastClientAlive= new AtomicLong();

    AtomicReference<PingStatus> myStatus = new AtomicReference<>(PingStatus.ONINIT);
    AtomicReference<PingStatus> remoteStatus = new AtomicReference<>(PingStatus.ONINIT);

    AtomicBoolean hasRemoteKey = new AtomicBoolean();

    @Override
    public final void onWrite(Message m) {
        ctx.writeAndFlush(Unpooled.wrappedBuffer(
                        intToBytes(MessageDecoder.MESSAGE_ID),
                        intToBytes(m.getSerializedSize()),
                        m.toByteArray()
                )
        );
    }

    @Override
    public boolean isOpen() {
        return ctx.channel().isOpen();
    }

    public ClientHandler(TcpBase server, String channelId, ChannelHandlerContext ctx) {
        super(server);

        this.ctx=ctx;
        this.channelId=channelId;
        this.remoteAddress = ctx!=null?ctx.channel().remoteAddress().toString():null;

        assert ctx != null;
        ctx.executor().scheduleAtFixedRate(this::sendPing, 0, 10, TimeUnit.SECONDS);

        log.info("active client ch: {}, addr: {}",
                ctx.channel().id().asShortText(),
                ctx.channel().remoteAddress().toString()
        );
    }

    @Override
    public final void onProsessMessage(Message m) {

        lastClientAlive.set(System.currentTimeMillis());

        try {

            if(m.hasAesKey() && hasRemoteKey.get()) {
                if(m.getAesKey().getId()== remoteKeyId.get()) {
                    byte[] b = m.getAesKey().getKey().toByteArray();
                    remoteAESkey.set(Encrypt.decrypt(b,getServer().getRsaKey().getPrivate()));
                }
            }

            if(m.getType()== MessageType.PING) {
                remoteStatus.set(m.getPing().getStatus());

            } else if(m.getType()== MessageType.INIT) {

                if(myStatus.get()==PingStatus.ONINIT) {
                    myStatus.set(PingStatus.HASINIT);
                }

                remoteClientId.set(m.getInit().getClientId());
                remoteLocalAddress.set(m.getInit().getLocalAddr());
                remoteLocalPort.set(m.getInit().getLocalPort());

                getServer().getActiveClients().put(remoteClientId.get(),this);

                sendPing();

            } else if(m.getType()== MessageType.PUBLIC_KEY) {

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
                log.info("GOT KEY, ch: {}, KEYID: {}, addr: {}",
                        channelId,
                        remoteKeyId.get(),
                        remoteAddress
                );
                sendPing();

//            } else if(m.getType()== MessageType.MESSAGE) {
//            } else if(m.getType()== MessageType.REQUEST) {
//            } else if(m.getType()== MessageType.REPLY) {
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

    @Override
    public void sendMessage(String message) {
        if(hasRemoteKey.get()) {
            super.sendMessage(message);
        } else {
            log.warn("{} -> ch: {}, NO KEY REPLY addr: {}",
                    getServer().getClientId(),
                    channelId,
                    remoteAddress
            );
        }
    }


    @Override
    public final void onInit() {

        final ClientHandler client = this;
        getServer().getExecutor().execute(()-> {

            int cnt=0;
            while(client.remoteStatus.get().getNumber() < PingStatus.HASINIT.getNumber() && cnt < 10) {

                Message m = Message.newBuilder()
                        .setType(MessageType.INIT)
                        .setInit(Init.newBuilder()
                                .setClientId(getServer().getClientId())
                                .setLocalAddr(getServer().getLocalAddress())
                                .setLocalPort(getServer().getLocalPort())
                                .build()
                        )
                        .build();

                write(m);
                outWork.ping.incrementAndGet();

                write(m);
                outWork.key.incrementAndGet();

                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                }
                cnt++;
            }

            cnt=0;
            while(client.remoteStatus.get().getNumber() < PingStatus.HASKEY.getNumber() && cnt < 10) {

                Message m = Message.newBuilder()
                        .setType(MessageType.PUBLIC_KEY)
                        .setPublicKey(MessageOuterClass.PublicRsaKey.newBuilder()
                                .setId(getServer().getRsaKey().getPublic().hashCode())
                                .setKey(ByteString.copyFrom(getServer().getRsaKey().getPublic().getEncoded()))
                                .build()
                        )
                        .build();


                write(m);
                outWork.key.incrementAndGet();

                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                }
                cnt++;
            }

            if(client.remoteStatus.get().getNumber() < PingStatus.HASKEY.getNumber()) {
                log.warn("REMOTE MISSING INIT ch: {}, status: {}, addr: {}",
                        channelId,
                        client.remoteStatus.get(),
                        remoteAddress
                );
            } else {
                hasRemoteKey.set(true);
            }

        });
    }

}
