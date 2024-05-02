package m2.proxy.executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class ServiceBaseExecutor extends ServiceBase {

    private static final Logger log = LoggerFactory.getLogger(ServiceBaseExecutor.class);

    private ExecutorService executor;
    public ExecutorService getExecutor() {
        if(executor==null) {
            executor = Executors.newCachedThreadPool();
        }
        return executor;
    }

    protected final AtomicBoolean stopped = new AtomicBoolean(false);
    public boolean isRunning() {
        return running.get() && !stopped.get();
    }

    @Override
    protected final void waitfor(long time)  {
        if(running.get() && !stopped.get()) {
            super.waitfor(time);
        }
        if (stopped.get()) {
            running.set(false);
        }
    }

    public void start(Duration wait)  {
        long start = System.currentTimeMillis();
        new Thread( () -> start() ).start();
        while (!isRunning()) {
            try {
                Thread.sleep( 100 );
            } catch (InterruptedException ignored) {
            }
        }
    }

    @Override
    public final void start() {

        if(!running.getAndSet(true)) {

            stopped.set(false);
            if(open()) {

                try {
                    getExecutor().execute(() -> {
                        try {
                            if(!stopped.get()) {
                                startServices();
                                execute();
                            }
                        } catch (RejectedExecutionException e) {
                            log.error("Reject execute: {}",e.getMessage());
                            stopped.set(true);
                        } catch (Exception e) {
                            log.error("Error execute: {}",e.getMessage());
                            stopped.set(true);
                        }
                        running.set(false);
                    });
                } catch (Throwable t) {
                    running.set(false);
                    stopped.set(true);
                    log.warn(t.getMessage());
                }

            } else {
                running.set(false);
                stopped.set(true);
                log.debug("Not open / stopped");
            }
        }
    }

    @Override
    public final void stop() {
        if(!stopped.getAndSet(true)) {
            log.debug("Stopping");
            while(running.get()) {
                forceClose();
                synchronized (waitObject) {
                    waitObject.notify();
                }
                try {
                    log.debug("wait running");
                    Thread.sleep(25);
                } catch (InterruptedException ignored) {
                }
            }

            close();

            executor.shutdownNow();
            executor= null;

            log.debug("stopped");
        }
    }

    protected abstract boolean open();
    protected abstract void startServices();
    protected abstract void execute();
    protected abstract void close();
    protected abstract void forceClose();

}
