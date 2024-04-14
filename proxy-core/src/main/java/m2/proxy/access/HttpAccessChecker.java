package m2.proxy.access;

import m2.proxy.EndpointPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class HttpAccessChecker {
    private static final Logger log = LoggerFactory.getLogger(AccessController.class);

    private final Map<String, Map<Long,SessionAccess>> accessIpAddresses;
    private final Map<String, Map<Long,SessionAccess>> accessTokenPath;
    private final Map<String, EndpointPath> endPoints;

    public HttpAccessChecker(Map<String, Map<Long, SessionAccess>> accessIpAddresses,
                             Map<String, Map<Long, SessionAccess>> accessTokenPath,
                             Map<String, EndpointPath> endPoints
    ) {

        this.accessIpAddresses=accessIpAddresses;
        this.accessTokenPath = accessTokenPath;
        this.endPoints=endPoints;
    }

    private SessionAccess getFromTokenPath(InetAddress ipAddress, String tokenPath) {
        AtomicReference<SessionAccess> ret = new AtomicReference<>();
        Map<Long,SessionAccess> paths = accessTokenPath.get(tokenPath);
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

    public SessionAccess getAccess(InetAddress inetAddress, List<String> headerLines, StringBuilder requestBuffer) {

        if(headerLines.get(0).startsWith("GET")) {

            String first_line = headerLines.get(0);
            String[] pathArr = first_line.replace("GET","").split("\\s+");
            String[] pathPart = pathArr[1].split("/");

            if(pathPart.length>1) {
                SessionAccess ret = getFromTokenPath(inetAddress,pathPart[1]);
                if(ret!=null) {
                    first_line = first_line.replace(ret.getTokenPath()+"/","");
                    // check if endpoint pat is in url
                    if(first_line.startsWith("/"+ret.getEndpointPath())) {
                        // endpoint
                        ret.setEndPoint(endPoints.getOrDefault(ret.getEndpointPath(),null));
                        first_line = first_line.replace("/"+ret.getEndpointPath(),"");
                    }
                    requestBuffer.append(first_line).append("\r\n");
                    for(int t=1;t<headerLines.size();t++){
                        requestBuffer.append(headerLines.get(t)).append("\r\n");
                    }
                }
                return ret;
            }
        }
        return new SessionAccess();
    }

}