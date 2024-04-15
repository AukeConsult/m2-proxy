package m2.proxy.httpserver;

import m2.proxy.EndpointPath;
import m2.proxy.access.AccessController;
import m2.proxy.ProxyServerBase;
import m2.proxy.types.TypeServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;

public class ProxyServerBaseHttpDebug extends ProxyServerBase {
    private static final Logger log = LoggerFactory.getLogger(ProxyServerBaseHttpDebug.class);

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

            StringBuilder requestString = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                    requestString.append(line+"\r\n");
                    //if(line.isEmpty()) break;
                    if(!reader.ready()) break;
                }

                log.info(requestString.toString());

                String content = "<html><body>" +
                        "<p>" +
                        client.getInetAddress().getHostName() + ":" + client.getPort() +
                        "</p>" +
                        "<p>" +
                        requestString.toString() +
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
                out.close();
                client.close();

            } catch (IOException e) {
                log.warn("{} -> RequestId: {}, error: {}", getServerId(), requestId, e.getMessage());
            } catch (Exception e) {
                log.warn("{} -> RequestId: {}, error: {}", getServerId(), requestId, e.getMessage());
            }
        });
    }

    public ProxyServerBaseHttpDebug(AccessController accessController,
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