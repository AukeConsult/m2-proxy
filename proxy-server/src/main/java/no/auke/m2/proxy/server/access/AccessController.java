package no.auke.m2.proxy.server.access;

import io.micronaut.context.annotation.ConfigurationProperties;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import no.auke.m2.proxy.base.ExtEndpoint;
import no.auke.m2.proxy.executors.ServiceBaseExecutor;
import no.auke.m2.proxy.types.TypeProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@ConfigurationProperties("access-setup")
public class AccessController extends ServiceBaseExecutor  {
    private static final Logger log = LoggerFactory.getLogger(AccessController.class);

    // parameters
    public long timeToLiveMinutes;
    public int checkPeriodSeconds;
    public List<Map<String,Object>> userAccessList = new ArrayList<>();
    public List<Map<String,Object>> proxyEndpointList = new ArrayList<>();
    // ---------------------------------------------------------------------

    private final Map<String, ExtEndpoint> endPoints = new ConcurrentHashMap<>();

    private final Map<String, Map<Long,SessionAccess>> accessServers = new ConcurrentHashMap<>();
    private final Map<String, Map<Long,SessionAccess>> accessIpAddresses = new ConcurrentHashMap<>();
    private final Map<String, Map<Long,SessionAccess>> accessFilterPath = new ConcurrentHashMap<>();

    public synchronized HttpAccessChecker getHttpAccessChecker() {
        return new HttpAccessChecker(accessServers,accessIpAddresses,accessFilterPath);
    }

    @PostConstruct
    public void init() {

        SessionAccess.rnd.setSeed(System.currentTimeMillis());

        proxyEndpointList.forEach(e -> {

            String endpointId = (String)e.getOrDefault("endpoint-id","default");
            TypeProtocol protocol = TypeProtocol.valueOf((String)e.getOrDefault("endpoint-type","http"));
            String endpointUrl = (String)e.getOrDefault("endpoint-url","");
            String endpointHostName = (String)e.getOrDefault("endpoint-host-name","localhost");
            int endpointPort = (int)e.getOrDefault("endpoint-port",3000);

            if(!endpointId.isEmpty()) {
                if(!endpointUrl.isEmpty()) {
                    endPoints.put(
                            endpointId,
                            new ExtEndpoint().setWithUrl(protocol,endpointId,endpointUrl)
                    );
                } else {
                    endPoints.put(
                            endpointId,
                            new ExtEndpoint().setWithHost(protocol,endpointId,endpointHostName,endpointPort)
                    );
                }
            }

        });

        userAccessList.forEach(e -> {

            String userId = (String)e.getOrDefault("user-id","default");
            String serverId = (String)e.getOrDefault("server-id","");
            String ipAddress = (String)e.getOrDefault("ip-address","");
            String endpointId = (String)e.getOrDefault("endpoint-id","default");
            String tokenPath = (String)e.getOrDefault("token-path","");
            String token = (String)e.getOrDefault("token","");

            ExtEndpoint endpoint = endPoints.getOrDefault(endpointId,null);
            SessionAccess access;
            if(!tokenPath.isEmpty()) {
                access = new SessionAccess(userId,serverId,ipAddress, timeToLiveMinutes*60*1000L, endpoint).setWithPath(
                        tokenPath
                );
            } else if(!token.isEmpty()) {
                access = new SessionAccess(userId,serverId,ipAddress, timeToLiveMinutes*60*1000L, endpoint).setWithToken(
                        token
                );
            } else {
                access = new SessionAccess(userId,serverId,ipAddress, timeToLiveMinutes*60*1000L, endpoint);
            }

            // add to list
            accessServers.putIfAbsent(serverId,new ConcurrentHashMap<>());
            accessIpAddresses.putIfAbsent(ipAddress,new ConcurrentHashMap<>());
            accessFilterPath.putIfAbsent(tokenPath,new ConcurrentHashMap<>());

            accessServers.get(serverId).put(access.getAccessId(),access);
            accessIpAddresses.get(ipAddress).put(access.getAccessId(),access);
            accessFilterPath.get(tokenPath).put(access.getAccessId(),access);

        });
    }


    @Override
    protected boolean open() {
        return true;
    }

    @Override
    protected void startServices() {}

    @Override
    protected void execute() {
        long waitTime = checkPeriodSeconds*1000L;
        while(isRunning()) {
            log.info("check access controller");
            // clean up invalid access
            waitfor(waitTime);
        }
    }
    @Override
    protected void close() {}
    @Override
    protected void forceClose() {}

}
