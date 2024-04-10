package no.auke.m2.proxy.server;

import no.auke.m2.proxy.server.base.ProxyMain;
import no.auke.m2.proxy.server.base.ProxyServer;
import no.auke.m2.proxy.types.TypeServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.Socket;

public class ProxyServerSftp extends ProxyServer {
    private static final Logger log = LoggerFactory.getLogger(ProxyServerSftp.class);

    @Override
    public Object readInput(BufferedReader inputStream) throws IOException {
        return null;
    }

    @Override
    protected void executeRequest(final ProxyServer proxyServer, final Socket clientSocket, long requestId) {

    }

    public ProxyServerSftp(ProxyMain mainService,
                           String serverId,
                           String bootAddress,
                           int port,
                           int inActiveTimeSeconds,
                           int corePooSize,
                           int maximumPoolSize,
                           int keepAliveTime

    ) {
        super(mainService,serverId,bootAddress,port,inActiveTimeSeconds,corePooSize,maximumPoolSize,keepAliveTime, TypeServer.SFTP);
    }

}