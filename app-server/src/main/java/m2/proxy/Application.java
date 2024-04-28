package m2.proxy;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.Micronaut;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.inject.Inject;
import m2.proxy.admin.AccessController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.Runtime.getRuntime;

@ConfigurationProperties("micronaut.application")
public class Application implements ApplicationEventListener<ServerStartupEvent> {
    private static final Logger log = LoggerFactory.getLogger(ProxyTcpServer.class);

    // parameter
    public String name;
    public int port;

    @Inject
    Server server;

    @Inject
    private AccessController accessController;

    public static void main(String[] args) {
        Micronaut.run(ProxyTcpServer.class, args);
    }

    @Override
    public void onApplicationEvent(ServerStartupEvent event) {

        log.info("{} -> Start server: http://localhost:{}",name,port);
        try {
            accessController.start();
            //proxyMain.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        getRuntime().addShutdownHook(new Thread(() -> {
            //proxyMain.stop();
            accessController.stop();
        }));

    }

}