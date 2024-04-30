package m2.proxy.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ProxyMetrics {
    private static final Logger log = LoggerFactory.getLogger( ProxyMetrics.class );

    public final AtomicInteger transIn = new AtomicInteger();
    public final AtomicInteger transRemoteOut = new AtomicInteger();
    public final AtomicInteger transDirectOut = new AtomicInteger();
    public final AtomicInteger transLocalOut = new AtomicInteger();
    public final AtomicInteger transServerOut = new AtomicInteger();
    public final AtomicInteger transError = new AtomicInteger();
    public final AtomicReference<String> clientId = new AtomicReference<>();

    public void printLog() {
        log.info( "{} -> Trans in: {}, " +
                        "err: {}, " +
                        "OUT-> remote: {}, direct: {}, local: {}, server: {}",
                clientId.get(),
                transIn.get(),
                transError.get(),
                transRemoteOut.get(),
                transDirectOut.get(),
                transLocalOut.get(),
                transServerOut.get()
        );
    }
    public void setId(String clientId) {
        this.clientId.set(clientId);
    }
}
