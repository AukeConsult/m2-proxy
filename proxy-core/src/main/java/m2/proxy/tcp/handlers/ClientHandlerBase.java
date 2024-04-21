package m2.proxy.tcp.handlers;

import com.google.protobuf.ByteString;
import m2.proxy.tcp.TcpBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.m2.MessageOuterClass.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class ClientHandlerBase {
    private static final Logger log = LoggerFactory.getLogger(ClientHandlerBase.class);

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

    private final TcpBase server;
    public TcpBase getServer() {return server;}

    final Map<Long, WaitRequest> requestSessions = new ConcurrentHashMap<>();

    final Map<Long, SessionHandler> sessions = new ConcurrentHashMap<>();
    public Map<Long, SessionHandler> getSessions() {
        return sessions;
    }

    protected String channelId="";
    protected String remoteAddress="";

    // where is where session is created
    public SessionHandler openSession(SessionHandler session, int sessionTimeOut) {
        session.handler=this;
        session.sessionTimeOut.set(System.currentTimeMillis()+sessionTimeOut);
        sessions.put(session.getSessionId(),session);
        return session;
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
        outWork.bytes.addAndGet(m.getSerializedSize());
        onWrite(m);
    }

    public ClientHandlerBase(TcpBase server) { this.server=server;}

    public void prosessMessage(Message m)  {

        try {

            if(m.getType()== MessageType.PING) {

                inWork.ping.incrementAndGet();
                inWork.bytes.addAndGet(m.getSerializedSize());

                log.debug("PING, {} -> ch: {}, Status: {}, remoteAddr: {}",
                        getServer().getClientId(),
                        channelId,
                        m.getPing().getStatus(),
                        remoteAddress
                );

            } else if(m.getType()== MessageType.INIT) {

                inWork.ping.incrementAndGet();
                inWork.bytes.addAndGet(m.getSerializedSize());

                log.info("INIT, {} -> ch: {}, remoteLocalAddr {}:{}, remoteAddr: {}",
                        getServer().getClientId(),
                        channelId,
                        m.getInit().getLocalAddr(),
                        m.getInit().getLocalPort(),
                        remoteAddress
                );

            } else if(m.getType()== MessageType.PUBLIC_KEY) {
                inWork.key.incrementAndGet();
                inWork.bytes.addAndGet(m.getSerializedSize());

            } else if(m.getType()== MessageType.MESSAGE) {

                inWork.message.incrementAndGet();
                inWork.bytes.addAndGet(m.getSerializedSize());

                log.debug("{} -> ch: {}, MESSAGE: {}, addr: {}",
                        getServer().getClientId(),
                        channelId,
                        m.getSubMessage().toStringUtf8(),
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
            onProsessMessage(m);

        } catch(Exception e) {
            log.warn("{} -> id: {} -> prosess exception: {}",getServer().getClientId(), channelId, e.toString());
        }

    }

    public void sendMessage(String message) {

        Message m = Message.newBuilder()
                .setType(MessageType.MESSAGE)
                .setSubMessage(ByteString.copyFromUtf8(message))
                .build();

        write(m);
        outWork.message.incrementAndGet();

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

    public abstract boolean isOpen();
    public abstract void onWrite(Message m);
    public abstract void onInit();

    public abstract void onProsessMessage(Message m);
    protected abstract void onRequest(long sessionId, long requestId, RequestType type, String address, ByteString request);

}
