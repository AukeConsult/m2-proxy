package no.auke.m2.proxy;

import jakarta.inject.Singleton;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.*;

@Singleton
public class MainService {

    BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
    ExecutorService executor = new ThreadPoolExecutor(10,20,2,TimeUnit.SECONDS,tasks);

    public void start() {

        try (ServerSocket serverSocket = new ServerSocket(3001)) {

            System.out.println("proxy, http://localhost:3001");
            while(true) {

                try {

                    Socket clientAccept = serverSocket.accept();
                    //Socket client = serverSocket.accept();
                    System.out.println("accept " + clientAccept.getInetAddress().getHostName() + ":" + clientAccept.getPort());

                    executor.submit(new Runnable() {

                        final Socket client = clientAccept;

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

                                    System.out.println("request " + client.getInetAddress().getHostName() + ":" + client.getPort());
                                    StringBuilder backend_response = new StringBuilder();
                                    int tries=0;
                                    while(tries<3) {

                                        try (Socket socket = new Socket("localhost", 3000)) {

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
                                                System.out.println("backend timeout, tries " + tries);
                                            } else {
                                                char c;
                                                while(in_node.ready() && (c = (char)in_node.read())>0) {
                                                    backend_response.append(c);
                                                }
                                                System.out.println("backend response, time " + (System.currentTimeMillis() - start_request));
                                                break;
                                            }

                                        } catch (UnknownHostException ex) {
                                            System.out.println("Server not found: " + ex.getMessage());
                                        } catch (IOException ex) {
                                            System.out.println("I/O error: " + ex.getMessage());
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
                                    System.out.println("request no content "  + client.getInetAddress().getHostName() + ":" + client.getPort());
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

                                //System.out.println(response);
                                client.close();

                            } catch (IOException e) {
                                System.out.println(e.getMessage());
                            } catch (InterruptedException ignored) {
                            }

                        }
                    }
                    );


                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public void stop() {

    }

}