package m2.proxy;

import m2.proxy.executors.ServiceBaseExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.security.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Netty extends ServiceBaseExecutor {

    protected Map<String, Handler> activeClients = new ConcurrentHashMap<>();
    public Map<String, Handler> getActiveClients() { return activeClients; }

    protected final String clientId;
    protected final String localHost;
    protected final int localPort;
    protected final KeyPair rsaKey;

    public Netty(String clientId, String localHost, int localPort, KeyPair rsaKey) {
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
        this.localHost=localHost;
        this.localPort=localPort;
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
    final protected void forceClose() { onStop(); }


}
