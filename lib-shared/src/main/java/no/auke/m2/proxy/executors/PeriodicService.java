package no.auke.m2.proxy.executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@SuppressWarnings("unused")
public abstract class PeriodicService extends ServiceBase implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(PeriodicService.class);
    private PeriodicTaskExecutor taskExecutor;

    private long delay = 100;
    protected void setDelay(long delay) {
        this.delay=delay;
    }
    public long getDelay() { return delay; }

    private long interval = 100;
    protected void setInterval(long interval) {
        this.interval=interval;
    }
    public long getInterval() { return interval; }

    private String taskId;
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    private final ReentrantLock lockExcecute = new ReentrantLock();

    @Override
    public final void start() {
        if(!running.getAndSet(true)) {
            if(initService()) {
                log.debug("{} -> service start",taskId);
                taskExecutor = new PeriodicTaskExecutor(
                        taskId,
                        delay,
                        interval,
                        this);
                taskExecutor.start();
            } else {
                running.set(false);
            }
        }
    }

    @Override
    public final void stop()   {
        if(running.getAndSet(false)) {
            new Thread(() -> {
                try {
                    if(lockExcecute.tryLock(1000, TimeUnit.MILLISECONDS)) {
                        log.debug("{} -> service stopping",taskId);
                        taskExecutor.stop();
                    } else {
                        log.debug("{} -> service can not stop, timed out. (execution time after stop to long)",taskId);
                        running.set(true);
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    if(lockExcecute.isHeldByCurrentThread()) {
                        lockExcecute.unlock();
                    }
                }
            }).start();
        }
    }

    public final void reStart() {
        if(!running.getAndSet(true)) {
            log.debug("{} -> service restart",taskId);
            taskExecutor = new PeriodicTaskExecutor(
                    taskId,
                    delay,
                    interval,
                    this);
            taskExecutor.start();
        }
    }

    @Override
    public final void run() {
        if(running.get()) {
            lockExcecute.lock();
            try {
                if(running.get()) {
                    long time = System.currentTimeMillis();
                    log.trace("{} -> service start execute",taskId);
                    execute();
                    log.debug("{} -> service executed, time ms {}",taskId,System.currentTimeMillis()-time);
                }
            } catch (RejectedExecutionException ex) {
                log.debug("{} -> service rejectedExecutionException",taskId);
            } finally {
                lockExcecute.unlock();
            }
        }
    }

    protected abstract boolean initService();
    protected abstract void execute();


}
