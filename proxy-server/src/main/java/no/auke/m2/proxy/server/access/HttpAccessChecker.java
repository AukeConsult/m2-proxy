package no.auke.m2.proxy.server.access;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class HttpAccessChecker {
    private static final Logger log = LoggerFactory.getLogger(AccessController.class);

    private final Map<String, Map<Long, SessionAccess>> accessServers;
    private final Map<String, Map<Long,SessionAccess>> accessIpAddresses;
    private final Map<String, Map<Long,SessionAccess>> accessFilterPath;

    public HttpAccessChecker(Map<String, Map<Long, SessionAccess>> accessServers,
                             Map<String, Map<Long, SessionAccess>> accessIpAddresses,
                             Map<String, Map<Long, SessionAccess>> accessFilterPath) {

        this.accessServers=accessServers;
        this.accessIpAddresses=accessIpAddresses;
        this.accessFilterPath=accessFilterPath;
    }

    public SessionAccess checkWithPath(InetAddress ipAddress, String filterPath) {
        AtomicReference<SessionAccess> ret = new AtomicReference<>();
        Map<Long,SessionAccess> paths = accessFilterPath.get(filterPath);
        if(paths!=null) {
            String host = ipAddress.getHostName();
            if(accessIpAddresses.containsKey(host)) {
                accessIpAddresses.get(ipAddress.getHostName()).values().forEach(s -> {
                    if(paths.containsKey(s.getAccessId())) {
                        ret.set(s);
                    }
                });
            }
            if(ret.get()==null) {
                accessIpAddresses.get("").values().forEach(s -> {
                    if(paths.containsKey(s.getAccessId())) {
                        ret.set(s);
                    }
                });
            }
        }
        return ret.get();
    }

    public SessionAccess checkHttpHeader(InetAddress inetAddress, List<String> headerLines, StringBuilder request) {
        if(headerLines.get(0).startsWith("GET")) {
            String[] pathArr = headerLines.get(0).replace("GET","").split("\\s+");
            log.info("{}",pathArr);
            String[] pathPart = pathArr[1].split("/");
            if(pathPart.length>1) {
                SessionAccess ret = checkWithPath(inetAddress,pathPart[1]);
                if(ret!=null) {
                    String first_line = headerLines.get(0).replace(ret.getTokenPath()+"/","");
                    request.append(first_line).append("\r\n");
                    for(int t=1;t<headerLines.size();t++){
                        request.append(headerLines.get(t)).append("\r\n");
                    }
                }
                return ret;
            }
        }
        return null;
    }

}