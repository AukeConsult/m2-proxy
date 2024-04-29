package m2.proxy.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

public class ServerMetrics {
    private static final Logger log = LoggerFactory.getLogger( ServerMetrics.class );
    public final AtomicInteger transIn = new AtomicInteger();
    public final AtomicInteger transRemoteOut = new AtomicInteger();
    public final AtomicInteger transDirectOut = new AtomicInteger();
    public final AtomicInteger transLocalOut = new AtomicInteger();
    public final AtomicInteger transServerOut = new AtomicInteger();
    public final AtomicInteger transError = new AtomicInteger();

    public void printLog() {
        log.info( "Number of transactions in: {}, " +
                        "err: {}, " +
                        "OUT-> remote: {}, direct: {}, local: {}, server: {}",
                transIn.get(),
                transError.get(),
                transRemoteOut.get(),
                transDirectOut.get(),
                transLocalOut.get(),
                transServerOut.get()
        );
    }
}
