package no.auke.m2.proxy;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.env.Environment;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.runtime.Micronaut;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.inject.Inject;
import no.auke.m2.proxy.server.access.AccessController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static java.lang.Runtime.getRuntime;

@ConfigurationProperties("micronaut.application")
public class Application implements ApplicationEventListener<ServerStartupEvent> {
    private static final Logger log = LoggerFactory.getLogger(Application.class);

    // parameter
    public String name;
    public int port;

    @Inject
    ProxyMain proxyMain;

    @Inject
    protected Environment environment;

    @Inject
    private AccessController accessController;

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }

    @Override
    public void onApplicationEvent(ServerStartupEvent event) {

        @NonNull Optional<Integer> par_port = environment.getProperty("micronaut.server.port", Integer.class);
        par_port.ifPresent(integer -> this.port = integer);

        log.info("{} -> Start server: http://localhost:{}",name,port);

        accessController.start();
        proxyMain.start();

        getRuntime().addShutdownHook(new Thread(() -> {
            proxyMain.stop();
            accessController.stop();
        }));

    }

}