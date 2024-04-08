/*
 * Field Management System
 * Copyright (c) 2012 Aker Solutions AS. All rights reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Aker Solutions. The intellectual and technical
 * concepts contained herein are proprietary to Aker Solutions and
 * are protected by trade secret or copyright law. Dissemination of
 * this information or reproduction of this material is strictly
 * forbidden unless prior written permission is obtained from Aker
 * Solutions.
 */

package no.auke.m2.proxy.executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Executor for single thread runnable object.
 * Be aware that a thrown exception will be silently suppressed with no further threads.
 */
public class PeriodicTaskExecutor {
    private static final Logger log = LoggerFactory.getLogger(PeriodicTaskExecutor.class);

    private ScheduledExecutorService executorService;
    private final Long initialDelay;
    private final long period;
    private final Runnable runnable;
    private final String taskId;

    /**
     * Create a new executor
     *
     * @param initialDelay before first operation
     * @param period       time between first and second, second a third task execution....
     * @param runnable     the task to periodically run
     */
    public PeriodicTaskExecutor(String taskId, long initialDelay, long period, Runnable runnable) {
        this.initialDelay = initialDelay;
        this.period = period;
        this.runnable = runnable;
        this.taskId=taskId;
        log.debug("{} -> schedule task created",taskId);
    }

    /**
     * Start thread that executes the task.
     */
    public final void start() {
        synchronized (this) {
            if (executorService == null) {
                executorService = Executors.newSingleThreadScheduledExecutor(Executors.defaultThreadFactory());
                executorService.scheduleAtFixedRate(runnable, initialDelay, period, TimeUnit.MILLISECONDS);
                log.debug("{} -> schedule task started, initial delay(ms): {}, period(ms): {}",taskId,initialDelay,period);
            }
        }
    }

    /**
     * Stop thread that executes the task.
     */
    public final void stop() {
        log.debug("{} -> schedule task stopping",taskId);
        synchronized (this) {
//            try {
//                if (executorService != null) {
//                    executorService.shutdown();
//                    executorService.awaitTermination(1000, TimeUnit.MILLISECONDS);
//                }
//            } catch (Exception e) {
//                log.error("{} -> schedule task failed to stop, {}", taskId,e.getMessage());
//            }
            new Thread(() -> {
                try {
                    executorService.shutdown();
                    if(!executorService.awaitTermination(1000, TimeUnit.MILLISECONDS)){
                        log.error("{} -> schedule task, failed to shutdown",taskId);
                    } else {
                        log.debug("{} -> schedule task stopped",taskId);
                    }
                } catch (InterruptedException e) {
                    log.error("{} -> schedule task failed to stop, {}", taskId,e.getMessage());
                }
            }).start();
        }
    }
}
