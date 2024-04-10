package no.auke.m2.proxy.server.http;

import no.auke.m2.proxy.server.access.SessionAccess;
import no.auke.m2.proxy.server.base.ProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.net.Socket;

public class SessionHttpM2 extends SessionHttp {

    private static final Logger log = LoggerFactory.getLogger(SessionHttpM2.class);
    public SessionHttpM2(ProxyServer server , SessionAccess access) {
        super(server,access);
    }

    @Override
    protected boolean sendRemote(final Socket client,long requestId, BufferedReader inputStream, StringBuilder header) {
        return false;
    }

}
