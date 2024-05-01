package m2.proxy.tcp.handlers;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ConnectionWorkCount {

    public AtomicInteger ping = new AtomicInteger();
    public AtomicInteger key = new AtomicInteger();
    public AtomicInteger message = new AtomicInteger();
    public AtomicInteger request = new AtomicInteger();
    public AtomicInteger reply = new AtomicInteger();
    public AtomicLong bytes = new AtomicLong();


}