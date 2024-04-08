package no.auke.m2.proxy;

import io.micronaut.context.annotation.ConfigurationProperties;
import jakarta.inject.Singleton;
import no.auke.m2.proxy.executors.ServiceBase;
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
    private List<ProxyServer> proxyRunning = new ArrayList<>();

    @Override
    protected boolean open() {
        return true;
    }

    @Override
    protected void startServices() {
        log.info("Start instances");
        serverInstances.forEach(s -> {
            ProxyServer service = new ProxyServer(
                    (String) s.get("server-id"),
                    (String) s.getOrDefault("host-name","localhost"),
                    (Integer) s.get("port")
            );
            service.start();
            proxyRunning.add(service);
        });
    }

    @Override
    protected void execute() {
        long waitTime = 1000*30L;
        while(isRunning()) {
            proxyRunning.forEach(p -> {
                log.info("{} -> Running: {}, requests: {}",p.getServerId(), p.isRunning(), p.getRequests());
            });
            waitfor(waitTime);
        }
    }
    @Override
    protected void forceClose() {}
    @Override
    protected void close() {
        proxyRunning.forEach(s -> s.stop());
    }
}