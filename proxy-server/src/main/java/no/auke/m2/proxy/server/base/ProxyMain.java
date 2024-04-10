package no.auke.m2.proxy.server.base;

import io.micronaut.context.annotation.ConfigurationProperties;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import no.auke.m2.proxy.executors.ServiceBaseExecutor;
import no.auke.m2.proxy.server.ProxyServerFtp;
import no.auke.m2.proxy.server.ProxyServerSftp;
import no.auke.m2.proxy.server.access.AccessController;
import no.auke.m2.proxy.types.TypeServer;
import no.auke.m2.proxy.server.ProxyServerHttp;
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

    public static long MAX_ID=99999999999L;

    @Inject
    private AccessController accessController;
    public AccessController getAccesController() {
        return accessController;
    }

    @Override
    protected boolean open() {
        return true;
    }

    @Override
    protected void startServices() {

        log.debug("{} -> Start instances",mainHost);
        serverInstances.forEach(s -> {

            TypeServer serverType = TypeServer.valueOf((String) s.getOrDefault("server-type","http"));
            if(serverType==TypeServer.HTTP) {

                ProxyServer service = new ProxyServerHttp(this,
                        (String) s.get("server-id"),
                        (String) s.getOrDefault("boot-address",""),
                        (int) s.getOrDefault("port",3001),
                        (int) s.getOrDefault("inactive-time-seconds",60),
                        (int) s.getOrDefault("core-poolsize",5),
                        (int) s.getOrDefault("maximum-poolsize",20),
                        (int) s.getOrDefault("keep-alivetime",5)
                );
                service.start();
                servicesRunning.add(service);

            } else if(serverType==TypeServer.FTP) {

                ProxyServer service = new ProxyServerFtp(this,
                        (String) s.get("server-id"),
                        (String) s.getOrDefault("boot-address",""),
                        (int) s.getOrDefault("port",3001),
                        (int) s.getOrDefault("inactive-time-seconds",60),
                        (int) s.getOrDefault("core-poolsize",5),
                        (int) s.getOrDefault("maximum-poolsize",20),
                        (int) s.getOrDefault("keep-alivetime",5)
                );
                service.start();
                servicesRunning.add(service);

            } else if(serverType==TypeServer.SFTP) {

                ProxyServer service = new ProxyServerSftp(this,
                        (String) s.get("server-id"),
                        (String) s.getOrDefault("boot-address",""),
                        (int) s.getOrDefault("port",3001),
                        (int) s.getOrDefault("inactive-time-seconds",60),
                        (int) s.getOrDefault("core-poolsize",5),
                        (int) s.getOrDefault("maximum-poolsize",20),
                        (int) s.getOrDefault("keep-alivetime",5)
                );
                service.start();
                servicesRunning.add(service);

            }

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