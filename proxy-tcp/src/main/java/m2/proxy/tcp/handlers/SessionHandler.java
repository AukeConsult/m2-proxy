package m2.proxy.tcp.handlers;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import m2.proxy.tcp.TcpBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.m2.MessageOuterClass.*;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public abstract class SessionHandler {
    private static final Logger log = LoggerFactory.getLogger(SessionHandler.class);

    private final long sessionId;
    public long getSessionId() { return sessionId; }

    AtomicLong timeOut = new AtomicLong();
    AtomicLong lastExecute = new AtomicLong();
    AtomicLong sessionTimeOut = new AtomicLong();

    ClientHandlerBase handler;
    AtomicReference<Reply> reply = new AtomicReference<>();

    AtomicLong lastRequestId = new AtomicLong();
    public long getLastRequestId() { return lastRequestId.get();}

    public SessionHandler() {
        sessionId= TcpBase.rnd.nextLong(Long.MAX_VALUE);
        lastExecute.set(System.currentTimeMillis());
    }

    private final Object sendWait = new Object();
    private void write(Request r) {
        lastExecute.set(System.currentTimeMillis());
        Message m = Message.newBuilder()
                .setType(MessageType.REQUEST)
                .setRequest(r)
                .build();
        handler.write(m);
    }

    public synchronized ByteString sendRequest(ByteString message, RequestType type, int timeOut) throws Exception {

        try {

            synchronized (sendWait) {

                // encode message
                lastExecute.set(System.currentTimeMillis());
                this.timeOut.set(timeOut);

                ClientHandlerBase.WaitRequest req = new ClientHandlerBase.WaitRequest();
                req.sessionHandler = this;
                req.async = false;
                req.request = Request.newBuilder()
                        .setSessionId(this.sessionId)
                        .setType(type)
                        .setRequestId(req.requestId)
                        .setRequestMessage(message)
                        .build();

                handler.requestSessions.put(req.requestId, req);
                lastRequestId.set(req.requestId);
                log.info("{} -> {} -> send: {}", getSessionId(), req.requestId, message.toStringUtf8());

                reply.set(null);
                write(req.request);
                sendWait.wait(timeOut);
                if (reply.get() != null) {
                    return reply.get().getReplyMessage();
                } else {
                    throw new InterruptedException("TIMEOUT");
                }
            }

        } catch (InterruptedException e) {
            throw new Exception(e.getMessage());
        } finally {
            //log.info("{} -> finish: {}", getSessionId(), message.toStringUtf8());
            lastRequestId.set(0);
        }

    }

    public synchronized long sendAsyncRequest(ByteString message, RequestType type)  {
        // encode message

        ClientHandlerBase.WaitRequest req = new ClientHandlerBase.WaitRequest();
        req.sessionHandler=this;
        req.async=true;
        req.request = Request.newBuilder()
                .setSessionId(this.sessionId)
                .setType(type)
                .setRequestId(req.requestId)
                .setRequestMessage(message)
                .build();

        handler.requestSessions.put(req.requestId,req);

        log.info("{} -> {} -> send async: {}",getSessionId(),req.requestId,message.toStringUtf8());
        write(req.request);

        return req.request.getRequestId();

    }

    public void handleReply(ClientHandlerBase.WaitRequest req, Reply reply) {

        if(!req.async) {
            if(req.requestId == lastRequestId.get()) {
                lastExecute.set(System.currentTimeMillis());
                synchronized (sendWait) {
                    this.reply.set(reply);
                    sendWait.notifyAll();
                }
            }
        } else {
            lastExecute.set(System.currentTimeMillis());
            // decode message
            ByteString res = reply.getReplyMessage();

            if(reply.getType()==RequestType.PLAIN) {
                onReceive(req.requestId,res);
            } else if(reply.getType()==RequestType.HTTP) {
                try {
                    HttpReply r = HttpReply.parseFrom(res);
                    onReceive(req.requestId,r);
                } catch (InvalidProtocolBufferException e) {
                    throw new RuntimeException(e);
                }
            }
        }

    }
    public abstract void onReceive(long requestId, ByteString reply);
    public abstract void onReceive(long requestId, HttpReply reply);

}