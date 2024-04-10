package no.auke.m2.proxy.server.http;

import no.auke.m2.proxy.server.access.SessionAccess;
import no.auke.m2.proxy.server.base.ProxyServer;
import no.auke.m2.proxy.server.base.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class SessionHttp extends Session {
    private static final Logger log = LoggerFactory.getLogger(SessionHttp.class);

    public SessionHttp(ProxyServer server , SessionAccess access) {
        super(server,access);
    }

    protected String errorMessage = "";
    protected long startRequest=System.currentTimeMillis();
    protected AtomicInteger bytesOut= new AtomicInteger();
    protected AtomicInteger bytesIn= new AtomicInteger();

    protected abstract boolean sendRemote(final Socket client, long requestId, BufferedReader inputStream, StringBuilder header);

    protected void writeResponse(final Socket client, StringBuilder backendResponse) throws IOException {
        bytesIn.set(backendResponse.length());
        PrintWriter out = new PrintWriter(client.getOutputStream());
        out.println(backendResponse);
        out.flush();
    }

    protected boolean executeRequest(final Socket client, long requestId, BufferedReader inputStream, StringBuilder header) {

        if(!header.isEmpty()) {

            this.startRequest=System.currentTimeMillis();
            this.errorMessage = null;
            this.bytesOut.set(0);
            this.bytesIn.set(0);

            try {

                if(!sendRemote(client,requestId,inputStream,header)) {
                    String response =
                            "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: text/html\r\n" +
                                    "Content-Length: " + errorMessage.getBytes().length + "\r\n" +
                                    "\r\n" +
                                    errorMessage;

                    PrintWriter out = new PrintWriter(client.getOutputStream());
                    out.println(response);
                    out.flush();
                }

                setVolume(this.bytesIn.get(),this.bytesOut.get());

                log.info("{} -> {}, RequestId: {}, Endpoint: {}, Request: {}, Response: {}, time: {}, {}",
                        this.server.getServerId(),
                        getAccess().getTokenPath(),
                        requestId,
                        getAccess().getEndpointPath(),
                        this.bytesOut.get(),
                        this.bytesIn.get(),
                        (System.currentTimeMillis() - this.startRequest),
                        this.errorMessage
                );
                client.close();

            } catch (IOException e) {
                log.warn("{} -> {}, RequestId: {}, error: {}", server.getServerId(),
                        getAccess().getTokenPath(), requestId, e.getMessage());
            }
        }
        return false;
    }

}
