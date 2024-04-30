package m2.proxy.tcp;

import com.google.protobuf.ByteString;
import m2.proxy.executors.ServiceBaseExecutor;
import m2.proxy.tcp.handlers.ConnectionHandler;
import m2.proxy.tcp.handlers.SessionHandler;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.*;

public abstract class TcpBase extends ServiceBaseExecutor {

    public static final Random rnd = new Random();

    public static class Access {
        public String clientId;
        public String userId;
        public String accessPath;
        public String remoteAddress;
        public String accessToken;
        public String agent;
        public long expireTime;
    }

    private Map<String, Access> accessList = new ConcurrentHashMap<>();
    public Map<String, Access> getAccessList() { return accessList; }

    public boolean checkAccess(String accessPath, String remoteAddress, String accessToken, String agent) {
        if (onCheckAccess( accessPath, remoteAddress, accessToken, agent )) {
            return true;
        }
        return false;
    }
    ;
    public Optional<String> setAccess(String userId, String passWord, String remoteAddress, String accessToken, String agent) {
        Optional<String> accessPath = onSetAccess( userId, remoteAddress, accessToken, agent );
        if (accessPath.isPresent()) {
            if (!accessList.containsKey( accessPath.get() )) {
                Access a = new Access();
                a.accessPath = accessPath.get();
                a.accessToken = accessToken;
                a.userId = userId;
                a.expireTime = System.currentTimeMillis() + Duration.ofDays( 30 ).toMillis();
                a.agent = agent;
                a.remoteAddress = remoteAddress;
                accessList.put( a.accessPath, a );
            }
        }
        return accessPath;
    }

    protected abstract boolean onCheckAccess(String accessPath, String remoteAddress, String accessToken, String agent);
    protected abstract Optional<String> onSetAccess(String userId, String remoteAddress, String accessToken, String agent);

    protected final String clientId;
    protected final String serverAddr;
    protected final int serverPort;
    protected final KeyPair rsaKey;
    protected Map<String, ConnectionHandler> activeClients = new ConcurrentHashMap<>();

    private String localAddress;
    private int localPort;

    public String getClientId() { return clientId; }
    public String getLocalAddress() { return localAddress; }
    public void setLocalAddress(String localAddress) { this.localAddress = localAddress; }
    public int getLocalPort() { return localPort; }
    public void setLocalPort(int localPort) { this.localPort = localPort; }
    public KeyPair getRsaKey() { return rsaKey; }
    public String getServerAddr() { return serverAddr; }
    public int getServerPort() { return serverPort; }

    public Map<String, ConnectionHandler> getActiveClients() { return activeClients; }

    private final Executor taskPool;
    public Executor getTaskPool() { return taskPool; }

    public TcpBase(String clientId, String serverAddr, int serverPort, String localAddress, KeyPair rsaKey) {

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

        this.clientId = clientId;
        this.serverAddr = serverAddr == null ? "127.0.0.1" : serverAddr;
        this.serverPort = serverPort;
        //this.localAddress = localAddress==null?Network.localAddress():localAddress;
        this.localAddress = localAddress == null ? "127.0.0.1" : localAddress;

        final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
        taskPool = new ThreadPoolExecutor( 10, 50, 10, TimeUnit.SECONDS, tasks );
        rnd.setSeed( System.currentTimeMillis() );
    }



    public abstract ConnectionHandler setConnectionHandler();
    public abstract void connect(ConnectionHandler handler);
    public abstract void disConnect(ConnectionHandler handler);
    abstract void onStart();
    abstract void onStop();

    @Override final protected boolean open() { return true; }
    @Override
    protected void startServices() { onStart(); }
    @Override final protected void close() { onStop(); }
    @Override final protected void forceClose() { }
}
