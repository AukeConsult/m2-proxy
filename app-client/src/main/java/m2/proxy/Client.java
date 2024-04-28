package m2.proxy;


import io.micronaut.context.annotation.ConfigurationProperties;
import jakarta.inject.Singleton;
import m2.proxy.executors.ServiceBaseExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@ConfigurationProperties("proxy-client")
public class Client extends ServiceBaseExecutor {
    private static final Logger log = LoggerFactory.getLogger(Client.class);

    @Override
    protected boolean open() {
        return true;
    }
    @Override
    protected void startServices() {}

    @Override
    protected void execute() {
        long waitTime = 1000*30L;
        while(isRunning()) {
            waitfor(waitTime);
        }
    }
    @Override
    protected void forceClose() {}
    @Override
    protected void close() {}

}