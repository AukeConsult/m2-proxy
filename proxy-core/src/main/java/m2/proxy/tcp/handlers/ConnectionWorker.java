package m2.proxy.tcp.handlers;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class ConnectionWorker implements Runnable {

    public final AtomicBoolean running = new AtomicBoolean();
    public final AtomicBoolean connected = new AtomicBoolean();
    public final AtomicInteger connectionErrors = new AtomicInteger();

    private ConnectionHandler handler;
    public ConnectionHandler getHandler() { return handler; }
    public void setHandler(ConnectionHandler handler) {
        this.handler = handler;
    }

    public boolean isReady() {
        return running.get() && connected.get();
    }
    public abstract void disconnect(boolean notifyRemote);

}