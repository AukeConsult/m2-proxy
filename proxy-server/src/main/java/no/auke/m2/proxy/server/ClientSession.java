package no.auke.m2.proxy.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicLong;

public class ClientSession {
    private static final Logger log = LoggerFactory.getLogger(ClientSession.class);

    private final ProxyServer server;
    private final String clientId;
    private final InetAddress ipAddress;
    private final String externalHost;
    private final int externalPort;

    public String getClientId() {
        return clientId;
    }


    private final AtomicLong lastActive = new AtomicLong(System.currentTimeMillis());
    public long getLastActive() {
        return lastActive.get();
    }

    public ClientSession(ProxyServer server , String clientId, InetAddress ipAddress, String externalHost, int externalPort) {

        this.server=server;
        this.clientId = clientId;
        this.ipAddress = ipAddress;
        this.externalHost=externalHost;
        this.externalPort=externalPort;
    }

    public boolean checkClient() {
        return ipAddress!=null;
    }

    public void executeRequest(final Socket client, long requestId) {

        server.getRequestExecutor().submit(() -> {

            try {

                lastActive.set(System.currentTimeMillis());

                String content;
                String response;

                long start_request = System.currentTimeMillis();

                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                String line;
                StringBuilder client_request = new StringBuilder();
                while (in.ready() && (line = in.readLine()) != null) {
                    if(line.startsWith("GET /favicon.ico")) {
                        break;
                    }
                    client_request.append(line).append("\r\n");
                }
                if(!client_request.isEmpty()) {

                    log.info("{} -> {}, RequestId: {}, Call to external server, Request size: {}",server.getServerId(), clientId, requestId, client_request.length());
                    StringBuilder backend_response = new StringBuilder();

                    boolean connectError = false;
                    String errorMessage = "";
                    int tries=0;

                    while(tries<3 && !connectError) {

                        try (Socket socket = new Socket(externalHost, externalPort)) {

                            PrintWriter out_node = new PrintWriter(socket.getOutputStream());
                            out_node.println(client_request);
                            out_node.flush();

                            InputStreamReader in_node = new InputStreamReader(socket.getInputStream());
                            int cnt=0;
                            while(!in_node.ready() && cnt<10) {
                                Thread.sleep(5);
                                cnt++;
                            }
                            if(!in_node.ready()) {
                                tries++;
                                log.info("{} -> {}, RequestId: {}, backend timeout, tries: {}",server.getServerId(), clientId, requestId, tries);
                            } else {
                                char c;
                                while(in_node.ready() && (c = (char)in_node.read())>0) {
                                    backend_response.append(c);
                                }
                                log.info("{} -> {}, RequestId: {}, backend response, time: {}",server.getServerId(), clientId, requestId, (System.currentTimeMillis() - start_request));
                                break;
                            }

                        } catch (UnknownHostException ex) {
                            log.info("{} -> {}, RequestId: {}, {}:{}, Unknown: {}",server.getServerId(), clientId, requestId, externalHost,externalPort,ex.getMessage());
                            connectError=true;
                            errorMessage="Unknown: " + ex.getMessage();
                        } catch (IOException ex) {
                            log.info("{} -> {}, RequestId: {}, {}:{}, Io: {}",server.getServerId(), clientId, requestId, externalHost,externalPort,ex.getMessage());
                            connectError=true;
                            errorMessage="IO: " + ex.getMessage();
                        }
                    }

                    if(!backend_response.isEmpty()) {
                        response = backend_response.toString();
                    } else if (tries>=3) {
                        errorMessage="external server do not respond";
                        response =
                                "HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: text/html\r\n" +
                                        "Content-Length: " + errorMessage.getBytes().length + "\r\n" +
                                        "\r\n" +
                                        errorMessage;

                    } else if (connectError) {
                        // set error codes
                        response =
                                "HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: text/html\r\n" +
                                        "Content-Length: " + errorMessage.getBytes().length + "\r\n" +
                                        "\r\n" +
                                        errorMessage;

                    } else {
                        content = "<html><body>" +
                                "<p>" +
                                client.getInetAddress().getHostName() + ":" + client.getPort() +
                                "</p>" +
                                "<p>" +
                                "Timeout" +
                                "</p>" +
                                "</body></html>";
                        response =
                                "HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: text/html\r\n" +
                                        "Content-Length: " + content.getBytes().length + "\r\n" +
                                        "\r\n" +
                                        content;
                    }

                } else {
                    log.info("{} -> {}, RequestId: {}, request no content", server.getServerId(), clientId, requestId);
                    response = "HTTP/1.1 200 OK\r\n";
                }

                PrintWriter out = new PrintWriter(client.getOutputStream());
                out.println(response);
                out.flush();
                client.close();

            } catch (IOException e) {
                log.warn("{} -> {}, RequestId: {}, error: {}", server.getServerId(), clientId, requestId, e.getMessage());
            } catch (InterruptedException ignored) {
            }
        });
    }


}
