package no.auke.m2.proxy.server;

import io.micronaut.context.annotation.ConfigurationProperties;
import jakarta.inject.Singleton;
import no.auke.m2.proxy.executors.ServiceBaseExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Singleton
@ConfigurationProperties("proxy-server")
public class ProxyMain extends ServiceBaseExecutor {
    private static final Logger log = LoggerFactory.getLogger(ProxyMain.class);

    public String mainHost;
    public List<Map<String,Object>> serverInstances = new ArrayList<>();

    private final List<ProxyServer> servicesRunning = new ArrayList<>();

    @Override
    protected boolean open() {
        return true;
    }

    @Override
    protected void startServices() {
        log.debug("{} -> Start instances",mainHost);
        serverInstances.forEach(s -> {
            ProxyServer service = new ProxyServer(
                    (String) s.get("server-id"),
                    (String) s.getOrDefault("host-name","localhost"),
                    (Integer) s.get("port")
            );
            service.start();
            servicesRunning.add(service);
        });
    }

    @Override
    protected void execute() {
        long waitTime = 1000*30L;
        while(isRunning()) {

            servicesRunning.forEach(p -> {

                p.cleanSessions();
                log.info("{} -> Running: {}, requests: {}, waiting: {}, Active sessions: {}",
                        p.getServerId(),
                        p.isRunning(),
                        p.getRequests(),
                        p.getWaitingTasks(),
                        p.getActiveSessions()
                );

            });

            waitfor(waitTime);
        }
    }
    @Override
    protected void forceClose() {}
    @Override
    protected void close() {
        servicesRunning.forEach(ServiceBaseExecutor::stop);
    }
}