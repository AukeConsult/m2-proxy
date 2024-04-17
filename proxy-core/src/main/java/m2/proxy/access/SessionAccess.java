package m2.proxy.access;

import java.util.Random;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class SessionAccess {

    public static Random rnd = new Random();

    private final long startTime=System.currentTimeMillis();
    private final AtomicLong lastAccess = new AtomicLong();
    private final AtomicLong sessionCount = new AtomicLong();
    private final AtomicLong transCount = new AtomicLong();
    private final AtomicLong bytesIn = new AtomicLong();
    private final AtomicLong bytesOut = new AtomicLong();

    private final long timeToLive;
    private final String endpointPath;

    private final String tokenPath;
    private final String securityToken;
    private final Set<String> ipAddresses;
    private final Map<String,String> headers;
    private final long accessId = rnd.nextLong(AccessController.MAX_ID);

    public final String getTokenPath() { return tokenPath; }
    public final String getEndpointPath() { return endpointPath;}
    public long getAccessId() {
        return accessId;
    }

    private EndpointPath endPoint;
    public EndpointPath getEndPoint() {return endPoint;}
    public void setEndPoint(EndpointPath endPoint) {this.endPoint = endPoint;}

    public SessionAccess() {
        this.tokenPath = null;
        this.endpointPath=null;
        this.securityToken = null;
        this.timeToLive = 0;
        this.ipAddresses=null;
        this.headers=null;
    }

    public SessionAccess(
            String tokenPath,
            String endpointPath,
            String securityToken,
            long timeToLive,
            Set<String> ipAddresses,
            Map<String,String> headers
    ) {

        this.tokenPath = tokenPath;
        this.endpointPath=endpointPath;
        this.securityToken = securityToken;
        this.timeToLive = timeToLive;
        this.ipAddresses=ipAddresses;
        this.headers=headers;
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

    public boolean isOk() {
        return isValid() && endPoint!=null;
    }
    public boolean isValid() {
        return System.currentTimeMillis()-startTime<timeToLive;
    }

}
