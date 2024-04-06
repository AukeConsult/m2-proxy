package no.auke.m2.proxy;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Main {

    List<IncomingClient> clients = new ArrayList<>();

    public void runServer() {
        try {
            System.out.println("proxy, http://localhost:3001");

            ServerSocket serverSocket = new ServerSocket(3001);
            Socket node_server = new Socket("localhost", 3000);
            while(true) {

                try (Socket client = serverSocket.accept()) {
                    //Socket client = serverSocket.accept();
                    System.out.println("accept " + client.getInetAddress().getHostAddress() + ":" + client.getPort());

                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(client.getInputStream()));
                    String line;
                    while (in.ready() && (line = in.readLine()) != null) {
                        System.out.println(line);
                    }
                    System.out.println("finish");

                    //clients.add(new IncomingClient(client));
                    //node_server.getOutputStream().write(request);
                    //node_server.getOutputStream().flush();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public static void main(String[] args) throws IOException {
        new Main().runServer();
    }
}