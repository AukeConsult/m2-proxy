package m2.proxy.tcp.handlers;

import com.google.protobuf.ByteString;
import m2.proxy.common.ProxyStatus;
import m2.proxy.common.TcpException;
import m2.proxy.proto.MessageOuterClass.*;
import m2.proxy.tcp.TcpBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public abstract class SessionHandler {
    private static final Logger log = LoggerFactory.getLogger(SessionHandler.class);

    private final long sessionId;
    public long getSessionId() { return sessionId; }

    AtomicLong timeOut = new AtomicLong();
    AtomicLong lastExecute = new AtomicLong();
    AtomicLong sessionTimeOut = new AtomicLong();

    ConnectionHandler handler;
    public ConnectionHandler getHandler() { return handler; }

    AtomicReference<Reply> reply = new AtomicReference<>();

    AtomicLong lastRequestId = new AtomicLong();
    public long getLastRequestId() { return lastRequestId.get();}

    public SessionHandler() {
        sessionId= TcpBase.rnd.nextLong(Long.MAX_VALUE);
        lastExecute.set(System.currentTimeMillis());
    }
    public SessionHandler(Long sessionId) {
        this.sessionId = sessionId;
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

    public synchronized ByteString sendRequest(String destination, ByteString message, RequestType type, int timeOut) throws TcpException {

        if(getHandler().isOpen()) {
            try {

                synchronized (sendWait) {

                    // encode message
                    lastExecute.set(System.currentTimeMillis());
                    this.timeOut.set(timeOut);

                    ConnectionHandler.WaitRequest req = new ConnectionHandler.WaitRequest();
                    req.sessionHandler = this;
                    req.async = false;
                    req.request = Request.newBuilder()
                            .setSessionId(this.sessionId)
                            .setType(type)
                            .setRequestId(req.requestId)
                            .setRequestMessage(message)
                            .setDestination(destination)
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
                        throw new TcpException(ProxyStatus.TIMEOUT,"timeout");
                    }
                }

            } catch (InterruptedException e) {
                throw new TcpException(ProxyStatus.TIMEOUT,"");
            } finally {
                //log.info("{} -> finish: {}", getSessionId(), message.toStringUtf8());
                lastRequestId.set(0);
            }

        } else {
            throw new TcpException(ProxyStatus.NOTOPEN,"no open");
        }
    }

    public synchronized long sendAsyncRequest(String destination, ByteString message, RequestType type) throws TcpException {

        if(getHandler().isOpen()) {
            // encode message

            ConnectionHandler.WaitRequest req = new ConnectionHandler.WaitRequest();
            req.sessionHandler=this;
            req.async=true;
            req.request = Request.newBuilder()
                    .setSessionId(this.sessionId)
                    .setType(type)
                    .setRequestId(req.requestId)
                    .setRequestMessage(message)
                    .setDestination(destination)
                    .build();

            handler.requestSessions.put(req.requestId,req);

            log.info("{} -> {} -> send async: {}",getSessionId(),req.requestId,message.toStringUtf8());
            write(req.request);

            return req.request.getRequestId();

        } else {
            throw new TcpException(ProxyStatus.NOTOPEN,"no open");
        }

    }

    public void handleReply(ConnectionHandler.WaitRequest req, Reply reply) {

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
                onReceive(req.requestId,res);
            }
        }

    }
    public abstract void onReceive(long requestId, ByteString reply);

}
