package m2.proxy;

import m2.proxy.executors.ServiceBaseExecutor;
import java.security.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Netty extends ServiceBaseExecutor {

    protected Map<String, Handler> activeClients = new ConcurrentHashMap<>();
    public Map<String, Handler> getActiveClients() { return activeClients; }

    protected final String clientId;
    protected final String serverAddr;
    protected final int serverPort;

    private String localAddress;
    private int localPort;

    public String getLocalAddress() { return localAddress;}
    public void setLocalAddress(String localAddress) { this.localAddress = localAddress; }
    public int getLocalPort() { return localPort; }
    public void setLocalPort(int localPort) { this.localPort = localPort;}

    protected final KeyPair rsaKey;

    public Netty(String clientId, String serverAddr, int serverPort, String localAddress, KeyPair rsaKey) {

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

    }

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
