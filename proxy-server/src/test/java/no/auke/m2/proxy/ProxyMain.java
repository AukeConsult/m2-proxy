package no.auke.m2.proxy;

import io.micronaut.context.annotation.ConfigurationProperties;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import no.auke.m2.proxy.executors.ServiceBaseExecutor;
import no.auke.m2.proxy.server.access.AccessController;
import no.auke.m2.proxy.server.base.EndpointPath;
import no.auke.m2.proxy.server.base.ProxyServer;
import no.auke.m2.proxy.server.ftp.ProxyServerFtp;
import no.auke.m2.proxy.server.http.ProxyServerHttp;
import no.auke.m2.proxy.server.sftp.ProxyServerSftp;
import no.auke.m2.proxy.types.TransportProtocol;
import no.auke.m2.proxy.types.TypeServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
@ConfigurationProperties("proxy-server")
public class ProxyMain extends ServiceBaseExecutor {
    private static final Logger log = LoggerFactory.getLogger(ProxyMain.class);

    public String mainHost;
    public List<Map<String,Object>> serverInstances = new ArrayList<>();
    private final List<ProxyServer> servicesRunning = new ArrayList<>();

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

            Map<String, EndpointPath> endPoints = new HashMap<>();

            List<Map<String,Object>> eplist = (List<Map<String,Object>>)s.getOrDefault("endpoints",new HashMap<>());
            eplist.forEach(e -> {
                String path = (String)e.getOrDefault("path","");
                TransportProtocol transport = TransportProtocol.valueOf((String)e.getOrDefault("transport","TCP"));
                String host = (String)e.getOrDefault("host","localhost");
                int port = (int)e.getOrDefault("port",3000);
                endPoints.put(path,new EndpointPath(path,transport).setHost(host,port));
            });


            TypeServer serverType = TypeServer.valueOf((String) s.getOrDefault("server-type","http"));
            if(serverType==TypeServer.HTTP) {

                ProxyServer service = new ProxyServerHttp(accessController,
                        (String) s.get("server-id"),
                        (String) s.getOrDefault("boot-address",""),
                        (int) s.getOrDefault("port",3001),
                        (int) s.getOrDefault("inactive-time-seconds",60),
                        (int) s.getOrDefault("core-poolsize",5),
                        (int) s.getOrDefault("maximum-poolsize",20),
                        (int) s.getOrDefault("keep-alivetime",5),
                        endPoints
                );
                service.start();
                servicesRunning.add(service);

            } else if(serverType==TypeServer.FTP) {

                ProxyServer service = new ProxyServerFtp(accessController,
                        (String) s.get("server-id"),
                        (String) s.getOrDefault("boot-address",""),
                        (int) s.getOrDefault("port",3001),
                        (int) s.getOrDefault("inactive-time-seconds",60),
                        (int) s.getOrDefault("core-poolsize",5),
                        (int) s.getOrDefault("maximum-poolsize",20),
                        (int) s.getOrDefault("keep-alive", 5),
                        endPoints
                );
                service.start();
                servicesRunning.add(service);

            } else if(serverType==TypeServer.SFTP) {

                ProxyServer service = new ProxyServerSftp(accessController,
                        (String) s.get("server-id"),
                        (String) s.getOrDefault("boot-address",""),
                        (int) s.getOrDefault("port",3001),
                        (int) s.getOrDefault("inactive-time-seconds",60),
                        (int) s.getOrDefault("core-poolsize",5),
                        (int) s.getOrDefault("maximum-poolsize",20),
                        (int) s.getOrDefault("keep-alivetime",5),
                        endPoints
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