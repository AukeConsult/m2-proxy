package m2.proxy.tcp;

import io.netty.channel.ChannelHandlerContext;
import m2.proxy.executors.ServiceBaseExecutor;
import m2.proxy.tcp.handlers.ClientHandlerBase;

import java.security.*;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;

public abstract class TcpBase extends ServiceBaseExecutor {

    public static final Random rnd = new Random();



    protected final String clientId;
    protected final String serverAddr;
    protected final int serverPort;
    protected final KeyPair rsaKey;
    protected Map<String, ClientHandlerBase> activeClients = new ConcurrentHashMap<>();


    private String localAddress;
    private int localPort;

    public String getClientId() {return clientId;}
    public String getLocalAddress() { return localAddress;}
    public void setLocalAddress(String localAddress) { this.localAddress = localAddress; }
    public int getLocalPort() { return localPort; }
    public void setLocalPort(int localPort) { this.localPort = localPort;}
    public KeyPair getRsaKey() { return rsaKey;}
    public Map<String, ClientHandlerBase> getActiveClients() { return activeClients; }

    Executor sessionThreads = Executors.newCachedThreadPool();
    public Executor getSessionThreads() {
        return sessionThreads;
    }

    private final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
    private final Executor taskPool;
    public Executor getTaskPool() { return taskPool; }

    public TcpBase(String clientId, String serverAddr, int serverPort, String localAddress, KeyPair rsaKey) {

        if(rsaKey==null) {
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                this.rsaKey = generator.generateKeyPair();
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        } else {
            this.rsaKey=rsaKey;
        }

        this.clientId=clientId;
        this.serverAddr = serverAddr;
        this.serverPort = serverPort;
        //this.localAddress = localAddress==null?Network.localAddress():localAddress;
        this.localAddress = localAddress==null?"127.0.0.1":localAddress;

        taskPool = new ThreadPoolExecutor(10,50,10,TimeUnit.SECONDS,tasks);
        rnd.setSeed(System.currentTimeMillis());


    }

    public abstract ClientHandlerBase setClientHandler(String id, ChannelHandlerContext ctx);
    abstract void onStart();
    abstract void onStop();

    @Override
    final protected boolean open() { return true;}
    @Override
    protected void startServices() { onStart();}

    @Override
    final protected void close() { onStop();}
    @Override
    final protected void forceClose() {}

}
