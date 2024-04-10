package no.auke.m2.proxy.server.base;

import no.auke.m2.proxy.server.access.SessionAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public abstract class Session {
    private static final Logger log = LoggerFactory.getLogger(Session.class);

    public static Random rnd = new Random();

    private final AtomicLong lastActive = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong transCount = new AtomicLong();

    protected final ProxyServer server;
    private final SessionAccess access;

    public SessionAccess getAccess() {return access;}
    public EndpointPath getEndPoint() { return access.getEndPoint();}

    public long getLastActive() {
        return lastActive.get();
    }

    private final long sessionId;
    public long getSessionId() { return sessionId;}

    public Session(ProxyServer server , SessionAccess access) {
        this.server=server;
        this.access = access;
        this.access.setNewSession();
        sessionId = rnd.nextLong(ProxyServer.MAX_ID);
        log.trace("new session {}", sessionId);
    }
    public void setVolume(int bytesIn, int bytesOut) {
        access.setVolume(bytesIn,bytesOut);
        transCount.incrementAndGet();
    }
    boolean execute(final Socket client, long requestId, BufferedReader inputStream, StringBuilder header) {
        lastActive.set(System.currentTimeMillis());
        return executeRequest(client, requestId, inputStream, header);
    }
    protected abstract boolean executeRequest(final Socket client, long requestId, BufferedReader inputStream, StringBuilder header);

}
