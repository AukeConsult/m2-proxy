package m2.proxy.executors;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class ServiceBase {

    protected final AtomicBoolean running = new AtomicBoolean(false);
    public boolean isRunning() { return running.get(); }

    protected final Object waitObject=new Object();
    public void setRunning(boolean run) {
        running.set(run);
        if(!run) {
            synchronized (waitObject) {
                waitObject.notify();
            }
        }
    }

    protected void waitfor(long time)  {
        synchronized (waitObject) {
            try {
                waitObject.wait(time);
            } catch (InterruptedException ignored) {
            }
        }
    }

    public abstract void start();
    public abstract void stop();

}
