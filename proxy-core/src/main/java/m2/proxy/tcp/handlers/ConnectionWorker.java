package m2.proxy.tcp.handlers;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class ConnectionWorker implements Runnable {
    public AtomicBoolean running = new AtomicBoolean();
    private ConnectionHandler handler;
    public ConnectionHandler getHandler() { return handler; }
    public void setHandler(ConnectionHandler handler) {
        this.handler = handler;
    }

    public abstract void stopWorker();
}