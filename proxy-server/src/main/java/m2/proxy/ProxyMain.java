package m2.proxy;


import io.micronaut.context.annotation.ConfigurationProperties;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import m2.proxy.executors.ServiceBaseExecutor;
import m2.proxy.types.TransportProtocol;
import m2.proxy.types.TypeServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Singleton
@ConfigurationProperties("proxy-server")
public class ProxyMain extends ServiceBaseExecutor {
    private static final Logger log = LoggerFactory.getLogger(ProxyMain.class);

    public String mainHost;

    public List<Map<String,Object>> serverInstances = new ArrayList<>();

    @Inject
    private AccessController accessController;

    @Override
    protected boolean open() {
        return true;
    }

    @Override
    protected void startServices() {

        log.debug("{} -> Start instances",mainHost);
        serverInstances.forEach(s -> {

            Map<String, EndpointPath> endPoints = new HashMap<>();

            List<Map<String,Object>> eplist = (List<Map<String,Object>>)s.getOrDefault("endpoints",new ArrayList<>());
            eplist.forEach(e -> {
                String path = (String)e.getOrDefault("path","");
                TransportProtocol transport = TransportProtocol.valueOf((String)e.getOrDefault("transport","TCP"));
                String host = (String)e.getOrDefault("host","localhost");
                int port = (int)e.getOrDefault("port",3000);
                endPoints.put(path,new EndpointPath(path,transport).setHost(host,port));
            });

            TypeServer serverType = TypeServer.valueOf((String) s.getOrDefault("server-type","http"));
            if(serverType==TypeServer.HTTP) {


            } else if(serverType==TypeServer.DEBUG) {


            }

        });
    }

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