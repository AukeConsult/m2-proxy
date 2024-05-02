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

    protected final AtomicInteger reconnectTime = new AtomicInteger();
    protected final String myId;
    protected final String myAddress;
    protected final int myPort;
    private final String localAddress;
    protected int localPort;
    protected final KeyPair rsaKey;

    protected int corePoolSize = 2;
    protected int maximumPoolSize = 50;
    protected int keepAliveTime = 10;

    // basic parameteres
    public String myAddress() { return myAddress; }
    public int myPort() { return myPort; }
    public String myId() { return myId; }
    public KeyPair rsaKey() { return rsaKey; }
    public String localAddress() { return localAddress; }
    public int localPort() { return localPort; }

    public int pingPeriod() { return 10; }
    public int nettyWorkerThreads() { return 10; }
    public int nettyConnectThreads() { return 5; }
    public int reconnectTime() { return reconnectTime.get(); }

    public int corePoolSize() { return corePoolSize = 5; }
    public int maximumPoolSize() { return maximumPoolSize = 20; }
    public int keepAliveTime() { return keepAliveTime = 10; }

    public void setReconnectTimeSeconds(int reconnectTime) { this.reconnectTime.set( reconnectTime * 1000 ); }

    private final AtomicReference<String> publicAddress = new AtomicReference<>();
    private final AtomicInteger publicPort = new AtomicInteger();

    public String getPublicAddress() { return publicAddress.get(); }
    public void setPublicAddress(String publicAddress) { this.publicAddress.set( publicAddress ); }
    public AtomicInteger getPublicPort() { return publicPort; }
    public void setPublicPort(int publicPort) { this.publicPort.set( publicPort ); }

    private final Map<String, AccessCache> accessCacheList = new ConcurrentHashMap<>();
    public Map<String, AccessCache> getAccessCacheList() { return accessCacheList; }

    public boolean checkAccess(String accessPath, String clientAddress, String accessToken, String agent) {
        if (onCheckAccess( accessPath, clientAddress, accessToken, agent )) {
            return false;
        }
        return false;
    }

    public Optional<String> setAccess(String userId, String passWord, String clientAddress, String accessToken, String agent) {
        Optional<String> accessPath = onSetAccess( userId, passWord, clientAddress, accessToken, agent );
        if (accessPath.isPresent()) {
            if (!accessCacheList.containsKey( accessPath.get() )) {
                AccessCache a = new AccessCache();
                a.clientId = myId;
                a.accessPath = accessPath.get();
                a.accessToken = accessToken;
                a.userId = userId;
                a.expireTime = System.currentTimeMillis() + Duration.ofDays( 30 ).toMillis();
                a.agent = agent;
                a.clientAddress = clientAddress;
                accessCacheList.put( a.accessPath, a );
            }
        }
        return accessPath;
    }

    protected abstract boolean onCheckAccess(String accessPath, String clientAddress, String accessToken, String agent);
    protected abstract Optional<String> onSetAccess(String userId, String passWord, String clientAddress, String accessToken, String agent);

    public void setLocalPort(int localPort) { this.localPort = localPort; }

    private Map<String, ConnectionHandler> activeClients = new ConcurrentHashMap<>();
    public Map<String, ConnectionHandler> getActiveClients() { return activeClients; }

    protected ExecutorService taskPool;
    public Executor getTaskPool() {
        if (taskPool == null) {
            final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
            taskPool = new ThreadPoolExecutor( corePoolSize(), maximumPoolSize(), keepAliveTime(), TimeUnit.SECONDS, tasks );
        }
        return taskPool;
    }

    public TcpBase() {
        this.myId = "";
        this.myAddress = "";
        this.myPort = 0;
        this.localAddress = "";
        this.rsaKey = null;
    }
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
        this.localAddress = localAddress == null ? "127.0.0.1" : localAddress;
        rnd.setSeed( System.currentTimeMillis() );
    }

    public abstract ConnectionHandler setConnectionHandler();
    public abstract void connect(ConnectionHandler handler);
    public abstract void doDisconnect(ConnectionHandler handler);
    public abstract void onDisconnected(ConnectionHandler handler);
    public abstract void onStart();
    public abstract void onStop();

    @Override final protected boolean open() {
        return true;
    }

    @Override protected void startServices() { onStart(); }
    @Override final protected void close() {
        onStop();
        taskPool.shutdownNow();
        taskPool = null;
    }
    @Override final protected void forceClose() { }
}
