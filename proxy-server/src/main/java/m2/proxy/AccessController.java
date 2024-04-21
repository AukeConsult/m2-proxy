package m2.proxy;

import io.micronaut.context.annotation.ConfigurationProperties;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import m2.proxy.executors.ServiceBaseExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@ConfigurationProperties("access-setup")
public class AccessController extends ServiceBaseExecutor {
    private static final Logger log = LoggerFactory.getLogger(AccessController.class);

    public static long MAX_ID=99999999999L;

    // parameters
    public long timeToLiveMinutes;
    public int checkPeriodSeconds;
    public List<Map<String,Object>> accessList = new ArrayList<>();
    // ---------------------------------------------------------------------

//    private final Map<String, Map<Long, SessionAccess>> accessIpAddresses = new ConcurrentHashMap<>();
//    private final Map<String, Map<Long,SessionAccess>> accessTokenPath = new ConcurrentHashMap<>();
//
//    public synchronized HttpAccessChecker getHttpAccessChecker(Map<String, EndpointPath> endPoints) {
//        return new HttpAccessChecker(accessIpAddresses,accessTokenPath, endPoints);
//    }

    @PostConstruct
    public void init() {

//        SessionAccess.rnd.setSeed(System.currentTimeMillis());
//
//        accessList.forEach(e -> {
//
//            String tokenPath = (String)e.getOrDefault("token-path","");
//            String accessToken = (String)e.getOrDefault("access-token","");
//            String endpointPath = (String)e.getOrDefault("endpoint-path","");
//            int keepAlive = (int)e.getOrDefault("keep-alive",timeToLiveMinutes*60);
//
//            List<String> ip = (List<String>)e.getOrDefault("ip-addresses",Collections.emptyList());
//            Set<String> ipAddresses = new HashSet<>(ip);
//
//            Map<String,String> hdr = (Map<String,String>)e.getOrDefault("headers",Collections.emptyMap());
//            Map<String,String> headers = new HashMap<>(hdr);
//
//            SessionAccess access = new SessionAccess(tokenPath,endpointPath,accessToken,keepAlive,ipAddresses,headers);
//
//            // add to ip addr list
//            ipAddresses.forEach(adr -> {
//                accessIpAddresses.putIfAbsent(adr,new ConcurrentHashMap<>());
//                accessIpAddresses.get(adr).put(access.getAccessId(),access);
//            });
//
//            accessTokenPath.putIfAbsent(tokenPath,new ConcurrentHashMap<>());
//            accessTokenPath.get(tokenPath).put(access.getAccessId(),access);
//
//        });
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
