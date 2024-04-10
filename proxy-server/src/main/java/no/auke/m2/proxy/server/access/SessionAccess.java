package no.auke.m2.proxy.server.access;

import no.auke.m2.proxy.base.ExtEndpoint;
import no.auke.m2.proxy.server.base.ProxyMain;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.random.RandomGenerator;

public class SessionAccess {

    public static Random rnd = new Random();

    private long startTime;
    private AtomicLong lastAccess = new AtomicLong();
    private AtomicLong sessionCount = new AtomicLong();
    private AtomicLong transCount = new AtomicLong();
    private AtomicLong bytesIn = new AtomicLong();
    private AtomicLong bytesOut = new AtomicLong();

    private final String userId;
    private final String serverId;
    private final String ipAddress;
    private final long timeToLive;
    private final ExtEndpoint endpoint;

    private String tokenPath;
    private String token;
    private long accessId;

    public String getUserId() { return userId;}
    public String getTokenPath() { return tokenPath; }
    public ExtEndpoint getEndpoint() { return endpoint;}
    public long getAccessId() {
        return accessId;
    }
    public String getServerId() { return serverId;}

    public SessionAccess(String userId, String serverId, String ipAddress, long timeToLive, ExtEndpoint endpoint) {
        this.userId = userId;
        this.timeToLive = timeToLive;
        this.endpoint=endpoint;
        this.ipAddress=ipAddress;
        this.serverId=serverId;
        this.accessId = rnd.nextLong(ProxyMain.MAX_ID);
        this.startTime = System.currentTimeMillis();
    }

    public void setNewSession() {
        sessionCount.incrementAndGet();
    }

    public void setVolume(int bytesIn, int bytesOut) {
        transCount.incrementAndGet();
        lastAccess.set(System.currentTimeMillis());
        this.bytesIn.addAndGet(bytesIn);
        this.bytesOut.addAndGet(bytesOut);
    }

    public SessionAccess setWithPath(String tokenPath) {
        this.tokenPath = tokenPath;
        return this;
    }
    public SessionAccess setWithToken(String token) {
        this.token=token;
        return this;
    }

    public boolean isOk() {
        return isValid() && endpoint!=null;
    }
    public boolean isValid() {
        return System.currentTimeMillis()-startTime<timeToLive;
    }

}
