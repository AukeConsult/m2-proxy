package m2.proxy.tcp.handlers;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class ConnectionWorker implements Runnable {

    public final AtomicBoolean running = new AtomicBoolean();
    public final AtomicBoolean stopping = new AtomicBoolean();
    public final AtomicBoolean connected = new AtomicBoolean();

    private ConnectionHandler handler;
    public ConnectionHandler getHandler() { return handler; }
    public void setHandler(ConnectionHandler handler) {
        this.handler = handler;
    }

    public boolean isReady() { return running.get() && connected.get() && !stopping.get(); }
    public abstract void stop(boolean notifyRemote);

}