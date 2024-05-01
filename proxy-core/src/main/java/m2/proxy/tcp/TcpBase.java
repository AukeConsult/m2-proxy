package m2.proxy.tcp;

import m2.proxy.executors.ServiceBaseExecutor;
import m2.proxy.tcp.handlers.ConnectionHandler;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public abstract class TcpBase extends ServiceBaseExecutor {

    public static final Random rnd = new Random();

    public static class Access {
        public String clientId;
        public String userId;
        public String accessPath;
        public String clientAddress;
        public String accessToken;
        public String agent;
        public long expireTime;
    }

    private final AtomicReference<String> publicAddress = new AtomicReference<>();
    private final AtomicInteger publicPort = new AtomicInteger();

    public String getPublicAddress() { return publicAddress.get(); }
    public void setPublicAddress(String publicAddress) { this.publicAddress.set(publicAddress); }
    public AtomicInteger getPublicPort() { return publicPort; }
    public void setPublicPort(int publicPort) { this.publicPort.set(publicPort); }

    private Map<String, Access> accessList = new ConcurrentHashMap<>();
    public Map<String, Access> getAccessList() { return accessList; }

    public boolean checkAccess(String accessPath, String clientAddress, String accessToken, String agent) {
        if (onCheckAccess( accessPath, clientAddress, accessToken, agent )) {
            return false;
        }
        return false;
    }
    ;
    public Optional<String> setAccess(String userId, String passWord, String clientAddress, String accessToken, String agent) {
        Optional<String> accessPath = onSetAccess( userId, passWord, clientAddress, accessToken, agent );
        if (accessPath.isPresent()) {
            if (!accessList.containsKey( accessPath.get() )) {
                Access a = new Access();
                a.clientId= myId;
                a.accessPath = accessPath.get();
                a.accessToken = accessToken;
                a.userId = userId;
                a.expireTime = System.currentTimeMillis() + Duration.ofDays( 30 ).toMillis();
                a.agent = agent;
                a.clientAddress = clientAddress;
                accessList.put( a.accessPath, a );
            }
        }
        return accessPath;
    }

    protected abstract boolean onCheckAccess(String accessPath, String clientAddress, String accessToken, String agent);
    protected abstract Optional<String> onSetAccess(String userId, String passWord, String clientAddress, String accessToken, String agent);

    protected final String myId;
    protected final String myAddress;
    protected final int myPort;
    public String getMyAddress() { return myAddress; }
    public int getMyPort() { return myPort; }
    public String getMyId() { return myId; }

    protected final KeyPair rsaKey;
    protected Map<String, ConnectionHandler> activeClients = new ConcurrentHashMap<>();

    private String localAddress;
    private int localPort;

    public String getLocalAddress() { return localAddress; }
    public void setLocalAddress(String localAddress) { this.localAddress = localAddress; }
    public int getLocalPort() { return localPort; }
    public void setLocalPort(int localPort) { this.localPort = localPort; }
    public KeyPair getRsaKey() { return rsaKey; }

    public Map<String, ConnectionHandler> getActiveClients() { return activeClients; }

    private final Executor taskPool;
    public Executor getTaskPool() { return taskPool; }

    public TcpBase(String myId, String myAddress, int myPort, String localAddress, KeyPair rsaKey) {

        if (rsaKey == null) {
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance( "RSA" );
                generator.initialize( 2048 );
                this.rsaKey = generator.generateKeyPair();
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException( e );
            }
        } else {
            this.rsaKey = rsaKey;
        }

        this.myId = myId;
        this.myAddress = myAddress == null ? "127.0.0.1" : myAddress;
        this.myPort = myPort;
        //this.localAddress = localAddress==null?Network.localAddress():localAddress;
        this.localAddress = localAddress == null ? "127.0.0.1" : localAddress;

        final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
        taskPool = new ThreadPoolExecutor( 10, 50, 10, TimeUnit.SECONDS, tasks );
        rnd.setSeed( System.currentTimeMillis() );
    }

    public abstract ConnectionHandler setConnectionHandler();
    public abstract void connect(ConnectionHandler handler);
    public abstract void disConnect(ConnectionHandler handler);
    public abstract void onStart();
    public abstract void onStop();

    @Override final protected boolean open() { return true; }
    @Override protected void startServices() { onStart(); }
    @Override final protected void close() { onStop(); }
    @Override final protected void forceClose() { }
}
