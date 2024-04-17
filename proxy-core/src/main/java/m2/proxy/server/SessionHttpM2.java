package m2.proxy.server;

import m2.proxy.access.SessionAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.net.Socket;

public class SessionHttpM2 extends SessionHttp {

    private static final Logger log = LoggerFactory.getLogger(SessionHttpM2.class);
    public SessionHttpM2(ProxyServerBase server , SessionAccess access) {
        super(server,access);
    }

    @Override
    protected boolean sendRemote(final Socket client,long requestId, BufferedReader inputStream, StringBuilder header) {
        return false;
    }

}
