package no.auke.m2.proxy.server.http;

import no.auke.m2.proxy.server.access.SessionAccess;
import no.auke.m2.proxy.server.base.ProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

public class SessionHttpTcp extends SessionHttp {
    private static final Logger log = LoggerFactory.getLogger(SessionHttpTcp.class);

    public SessionHttpTcp(ProxyServer server , SessionAccess access) {
        super(server,access);
    }

    @Override
    protected boolean sendRemote(final Socket client, long requestId, BufferedReader inputStream, StringBuilder header) {

        int tries=0;
        while(tries<3) {

            StringBuilder backendResponse = new StringBuilder();
            try (Socket socket = new Socket(getEndPoint().host, getEndPoint().port)) {

                PrintWriter out_node = new PrintWriter(socket.getOutputStream());

                // read and write
                String line = header.toString();

                while(line != null) {
                    bytesOut.addAndGet(line.length());
                    out_node.println(line);
                    out_node.flush();
                    line = (String)server.readInput(inputStream);
                }

                InputStreamReader in_node = new InputStreamReader(socket.getInputStream());
                int cnt=0;
                while(!in_node.ready() && cnt<10) {
                    Thread.sleep(5);
                    cnt++;
                }
                if(!in_node.ready()) {
                    tries++;
                    log.debug("{} -> {}, RequestId: {}, Ext server: {}, Timeout, tries: {}",server.getServerId(),
                            getAccess().getTokenPath(),
                            requestId,
                            getAccess().getEndpointPath(),
                            tries
                    );
                } else {
                    char c;
                    while(in_node.ready() && (c = (char)in_node.read())>0) {
                        backendResponse.append(c);
                    }
                    writeResponse(client,backendResponse);
                    return true;
                }

            } catch (UnknownHostException ex) {
                log.info("{} -> {}, RequestId: {}, {}, Unknown: {}",server.getServerId(),
                        getAccess().getTokenPath(), requestId, getEndPoint().toString(),ex.getMessage());
                errorMessage="Unknown: " + ex.getMessage();
                return false;
            } catch (IOException ex) {
                log.info("{} -> {}, RequestId: {}, {}, Io: {}",server.getServerId(),
                        getAccess().getTokenPath(), requestId, getEndPoint().toString(),ex.getMessage());
                errorMessage="IO: " + ex.getMessage();
                return false;
            } catch (Exception ex) {
                log.info("{} -> {}, RequestId: {}, {}, Io: {}",server.getServerId(),
                        getAccess().getTokenPath(), requestId, getEndPoint().toString(),ex.getMessage());
                errorMessage="Exception: " + ex.getMessage();
                return false;
            }
        }
        if(tries==3) {
            errorMessage="Endpoint not respond, timeout";
        }
        return false;
    }

}
