package no.auke.m2.proxy.server;

import no.auke.m2.proxy.server.base.*;
import no.auke.m2.proxy.types.TypeProtocol;
import no.auke.m2.proxy.types.TypeServer;
import no.auke.m2.proxy.server.access.SessionAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ProxyServerHttp extends ProxyServer {
    private static final Logger log = LoggerFactory.getLogger(ProxyServerHttp.class);

    @Override
    public Object readInput(BufferedReader inputStream) throws IOException {
        String line = null;
        while (inputStream.ready() && (line = inputStream.readLine()) != null) {
            break;
        }
        return line;
    }

    @Override
    protected void executeRequest(final ProxyServer proxyServer, final Socket clientSocket, long requestId) {

        // validate incoming request
        getRequestExecutor().submit(() -> {

            try {

                BufferedReader inputStream = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                List<String> header = new ArrayList<>();
                header.add((String)readInput(inputStream));
                if(header.get(0)!=null) {
                    if(header.get(0).startsWith("GET /favicon.ico")) {
                        header.clear();
                    }
                }

                if(header.isEmpty()) {

                    log.info("{} -> RequestId: {}, request no content", getServerId(), requestId);
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream());
                    out.println("HTTP/1.1 200 OK\r\n");
                    out.flush();
                    clientSocket.close();

                } else {

                    // check / verify the request
                    // find path

                    String line=(String)readInput(inputStream);
                    while (line != null && !line.isEmpty()) {
                        header.add(line);
                        line=(String)readInput(inputStream);
                    }
                    header.add(line);
                    StringBuilder client_request = new StringBuilder();

                    SessionAccess access = getAccesController().getHttpAccessChecker()
                            .checkHttpHeader(
                                    clientSocket.getInetAddress(),
                                    header,
                                    client_request
                            );

                    if(access!=null && access.isOk()) {
                        if(!clientSessions.containsKey(access.getAccessId())) {
                            if(access.getEndpoint().typeProtocol == TypeProtocol.HTTP) {
                                Session session = new SessionHttp(proxyServer,access);
                                clientSessions.put(access.getAccessId(),session);
                                log.info("{} -> {}, User: {}, RequestId: {}, Open session, to external server: {}",
                                        getServerId(),
                                        access.getAccessId(),
                                        access.getUserId(),
                                        requestId,
                                        access.getEndpoint().toString()
                                );
                            }
                        }
                        executeSession(clientSessions.get(access.getAccessId()),clientSocket, requestId, inputStream, client_request);

                    } else {

                        // client not accepted for proxy
                        String content = "<html><body>" +
                                "<p>" +
                                clientSocket.getInetAddress().getHostName() + ":" + clientSocket.getPort() +
                                "</p>" +
                                "<p>" +
                                "not accepted" +
                                "</p>" +
                                "</body></html>";

                        PrintWriter out = new PrintWriter(clientSocket.getOutputStream());
                        out.println(
                                "HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: text/html\r\n" +
                                        "Content-Length: " + content.getBytes().length + "\r\n" +
                                        "\r\n" +
                                        content
                        );
                        out.flush();
                    }
                }
                clientSocket.close();

            } catch (IOException e) {
                log.warn("{} -> RequestId: {}, error: {}", getServerId(), requestId, e.getMessage());
            }
        });
    }

    public ProxyServerHttp(ProxyMain mainService,
                           String serverId,
                           String bootAddress,
                           int port,
                           int inActiveTimeSeconds,
                           int corePooSize,
                           int maximumPoolSize,
                           int keepAliveTime

    ) {
        super(mainService,serverId,bootAddress,port,inActiveTimeSeconds,corePooSize,maximumPoolSize,keepAliveTime, TypeServer.HTTP);
    }

}