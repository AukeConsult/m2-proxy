package no.auke.m2.proxy.server.sftp;

import no.auke.m2.proxy.server.access.AccessController;
import no.auke.m2.proxy.server.base.EndpointPath;
import no.auke.m2.proxy.server.base.ProxyServer;
import no.auke.m2.proxy.types.TypeServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.net.Socket;
import java.util.Map;

public class ProxyServerSftp extends ProxyServer {
    private static final Logger log = LoggerFactory.getLogger(ProxyServerSftp.class);

    @Override
    public Object readInput(BufferedReader inputStream) {
        return null;
    }

    @Override
    protected void executeRequest(final ProxyServer proxyServer, final Socket clientSocket, long requestId) {

    }

    public ProxyServerSftp(AccessController accessController,
                           String serverId,
                           String bootAddress,
                           int port,
                           int inActiveTimeSeconds,
                           int corePooSize,
                           int maximumPoolSize,
                           int keepAliveTime,
                           Map<String, EndpointPath> endPoints

    ) {
        super(accessController,serverId,bootAddress,port,inActiveTimeSeconds,corePooSize,maximumPoolSize,keepAliveTime, endPoints, TypeServer.SFTP);
        log.debug("create new SFTP service");
    }

}