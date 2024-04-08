package no.auke.m2.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class ClientSession {
    private static final Logger log = LoggerFactory.getLogger(ClientSession.class);

    private ProxyServer server;
    private String clientId;
    private InetAddress ipAddress;
    private String externalHost;
    private int externalPort;

    public ClientSession(ProxyServer server , String clientId, InetAddress ipAddress, String externalHost, int externalPort) {

        this.server=server;
        this.clientId = clientId;
        this.ipAddress=ipAddress;
        this.externalHost=externalHost;
        this.externalPort=externalPort;
        log.info("{} -> Open connection to {}:{}", clientId,externalHost,externalPort);
    }

    public void executeRequest(final Socket client) {


        server.executor.submit(new Runnable() {

            @Override
            public void run() {

                try {

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

                        log.info("{} -> {}, request",server.getServerId(), clientId);
                        StringBuilder backend_response = new StringBuilder();
                        int tries=0;
                        while(tries<3) {

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
                                    log.info("{} -> {}, backend timeout, tries: {}",server.getServerId(), clientId, tries);
                                } else {
                                    char c;
                                    while(in_node.ready() && (c = (char)in_node.read())>0) {
                                        backend_response.append(c);
                                    }
                                    log.info("{} -> {}, backend response, time: {}",server.getServerId(), clientId, (System.currentTimeMillis() - start_request));
                                    break;
                                }

                            } catch (UnknownHostException ex) {
                                log.info("{} -> {}, External server not found: {}",server.getServerId(), clientId, ex.getMessage());
                            } catch (IOException ex) {
                                log.info("{} -> {}, I/O error: {}",server.getServerId(), clientId,ex.getMessage());
                            }

                        }

                        if(!backend_response.isEmpty()) {
                            response = backend_response.toString();
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
                        log.info("{} -> {}, request no content", server.getServerId(), clientId);
                        response = "HTTP/1.1 200 OK\r\n";
//                                    response =
//                                            "HTTP/1.1 200 OK\r\n" +
//                                                    "Content-Type: text/html\r\n" +
//                                                    "Content-Length: " + String.valueOf(content.getBytes().length) + "\r\n" +
//                                                    "\r\n" +
//                                                    content;
                    }

                    PrintWriter out = new PrintWriter(client.getOutputStream());
                    out.println(response);
                    out.flush();
                    client.close();

                } catch (IOException e) {
                    log.warn("{} -> {}, error: {}",server.getServerId(), clientId,e.getMessage());
                } catch (InterruptedException ignored) {
                }

            }
        });
    }
}
