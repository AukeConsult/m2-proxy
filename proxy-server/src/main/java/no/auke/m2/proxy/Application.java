package no.auke.m2.proxy;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.Micronaut;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.inject.Inject;

import static java.lang.Runtime.getRuntime;

@ConfigurationProperties("micronaut.application")
public class Application implements ApplicationEventListener<ServerStartupEvent> {

    // parameter
    public String name;

    @Inject
    MainService mainService;

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }

    @Override
    public void onApplicationEvent(ServerStartupEvent event) {
        onStart();
        getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                onStop();
            }
        }));
    }

    public void onStart() {
        mainService.start();
    }

    public void onStop() {
        mainService.stop();
    }

}