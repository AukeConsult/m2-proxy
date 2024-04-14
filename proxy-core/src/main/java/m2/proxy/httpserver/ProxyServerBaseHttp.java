package m2.proxy.httpserver;

import m2.proxy.EndpointPath;
import m2.proxy.ProxyServerBase;
import m2.proxy.Session;
import m2.proxy.access.AccessController;
import m2.proxy.access.SessionAccess;
import m2.proxy.types.TransportProtocol;
import m2.proxy.types.TypeServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProxyServerBaseHttp extends ProxyServerBase {
    private static final Logger log = LoggerFactory.getLogger(ProxyServerBaseHttp.class);

    @Override
    public Object readInput(BufferedReader inputStream) throws IOException {
        if(inputStream.ready()) {
            return inputStream.readLine();
        }
        return null;
    }

    @Override
    protected void executeRequest(final ProxyServerBase proxyServerBase, final Socket client, long requestId) {

        // validate incoming request
        getRequestExecutor().submit(() -> {

            try {

                BufferedReader inputStream = new BufferedReader(new InputStreamReader(client.getInputStream()));

                List<String> header = new ArrayList<>();
                header.add((String)readInput(inputStream));
                if(header.get(0)!=null) {
                    if(header.get(0).startsWith("GET /favicon.ico")) {
                        header.clear();
                    }
                }

                if(header.isEmpty()) {

                    log.info("{} -> RequestId: {}, request no content", getServerId(), requestId);
                    PrintWriter out = new PrintWriter(client.getOutputStream());
                    out.println("HTTP/1.1 200 OK\r\n");
                    out.flush();
                    client.close();

                } else {

                    // check / verify the request
                    // find path

                    String line=(String)readInput(inputStream);
                    while (line != null && !line.isEmpty()) {
                        header.add(line);
                        line=(String)readInput(inputStream);
                    }
                    header.add(line);
                    StringBuilder requestBuffer = new StringBuilder();

                    SessionAccess access = getAccesController().getHttpAccessChecker(getEndPoints())
                            .getAccess(
                                    client.getInetAddress(),
                                    header,
                                    requestBuffer
                            );

                    if(access!=null && access.isOk()) {

                        if(!clientSessions.containsKey(access.getAccessId())) {
                            if(access.getEndPoint().transportProtocol == TransportProtocol.HTTP) {
                                Session session = new SessionHttpTcp(proxyServerBase,access);
                                clientSessions.put(access.getAccessId(),session);
                                log.info("{} -> {}, User: {}, RequestId: {}, Open session, to external server: {}",
                                        getServerId(),
                                        access.getAccessId(),
                                        access.getEndpointPath(),
                                        requestId,
                                        access.getEndPoint().toString()
                                );
                            }
                        }
                        executeSession(clientSessions.get(access.getAccessId()),client, requestId, inputStream, requestBuffer);

                    } else {

                        // client not accepted for proxy
                        String content = "<html><body>" +
                                "<p>" +
                                client.getInetAddress().getHostName() + ":" + client.getPort() +
                                "</p>" +
                                "<p>" +
                                "not accepted" +
                                "</p>" +
                                "</body></html>";

                        PrintWriter out = new PrintWriter(client.getOutputStream());
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
                client.close();

            } catch (IOException e) {
                log.warn("{} -> RequestId: {}, error: {}", getServerId(), requestId, e.getMessage());
            }
        });
    }

    public ProxyServerBaseHttp(AccessController accessController,
                               String serverId,
                               String bootAddress,
                               int port,
                               int inActiveTimeSeconds,
                               int corePooSize,
                               int maximumPoolSize,
                               int keepAliveTime,
                               Map<String, EndpointPath> endPoints

    ) {
        super(accessController,serverId,bootAddress,port,inActiveTimeSeconds,corePooSize,maximumPoolSize,keepAliveTime, endPoints, TypeServer.HTTP);
    }

}