package no.auke.m2.proxy.server;

import no.auke.m2.proxy.server.base.Session;
import no.auke.m2.proxy.server.base.ProxyServer;
import no.auke.m2.proxy.server.access.SessionAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

public class SessionHttp extends Session {
    private static final Logger log = LoggerFactory.getLogger(SessionHttp.class);

    public SessionHttp(ProxyServer server , SessionAccess session) {
        super(server,session);
    }

    protected boolean executeRequest(final Socket client, long requestId, BufferedReader inputStream, StringBuilder header) {

        if(!header.isEmpty()) {

            try {

                long start_request = System.currentTimeMillis();
                boolean connectError = false;
                String errorMessage = "";
                int tries=0;

                while(tries<3 && !connectError) {

                    try (Socket socket = new Socket(getEndpoint().host, getEndpoint().port)) {

                        PrintWriter out_node = new PrintWriter(socket.getOutputStream());

                        // read and write
                        String line = header.toString();
                        int size=0;
                        while(line != null && !line.isEmpty()) {

                            size+=line.length();

                            out_node.println(line);
                            out_node.flush();

                            line = (String)server.readInput(inputStream);
                            while (inputStream.ready() && (line = inputStream.readLine()) != null) {
                                break;
                            }
                        }
                        if(line!=null) {
                            out_node.println(line);
                            out_node.flush();
                        }

                        log.info("{} -> {}, RequestId: {}, Call to external server, Request size: {}",server.getServerId(),
                                getAccess().getUserId(), requestId, size);

                        StringBuilder backend_response = new StringBuilder();
                        InputStreamReader in_node = new InputStreamReader(socket.getInputStream());
                        int cnt=0;
                        while(!in_node.ready() && cnt<10) {
                            Thread.sleep(5);
                            cnt++;
                        }
                        if(!in_node.ready()) {
                            tries++;
                            log.info("{} -> {}, RequestId: {}, backend timeout, tries: {}",server.getServerId(),
                                    getAccess().getUserId(), requestId, tries);
                        } else {
                            char c;
                            while(in_node.ready() && (c = (char)in_node.read())>0) {
                                backend_response.append(c);
                            }
                            log.info("{} -> {}, RequestId: {}, backend response, time: {}",server.getServerId(),
                                    getAccess().getUserId(), requestId, (System.currentTimeMillis() - start_request));
                        }

                        PrintWriter out = new PrintWriter(client.getOutputStream());
                        out.println(backend_response);
                        out.flush();
                        client.close();
                        return true;

                    } catch (UnknownHostException ex) {
                        log.info("{} -> {}, RequestId: {}, {}, Unknown: {}",server.getServerId(),
                                getAccess().getUserId(), requestId, getEndpoint().toString(),ex.getMessage());
                        connectError=true;
                        errorMessage="Unknown: " + ex.getMessage();
                    } catch (IOException ex) {
                        log.info("{} -> {}, RequestId: {}, {}, Io: {}",server.getServerId(),
                                getAccess().getUserId(), requestId, getEndpoint().toString(),ex.getMessage());
                        connectError=true;
                        errorMessage="IO: " + ex.getMessage();
                    } catch (Exception ex) {
                        log.info("{} -> {}, RequestId: {}, {}, Io: {}",server.getServerId(),
                                getAccess().getUserId(), requestId, getEndpoint().toString(),ex.getMessage());
                        connectError=true;
                        errorMessage="Exception: " + ex.getMessage();
                    }
                }

                String content;
                String response;

                if (tries>=3) {
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

                PrintWriter out = new PrintWriter(client.getOutputStream());
                out.println(response);
                out.flush();
                client.close();

            } catch (IOException e) {
                log.warn("{} -> {}, RequestId: {}, error: {}", server.getServerId(),
                        getAccess().getUserId(), requestId, e.getMessage());
            }
        }
        return false;
    }
}
