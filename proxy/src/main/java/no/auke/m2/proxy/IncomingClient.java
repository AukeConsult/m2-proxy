package no.auke.m2.proxy;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class IncomingClient {
    Socket client;
    public IncomingClient(Socket client) {
        this.client = client;
        execute();
    }
    private void execute() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("started " + client.getInetAddress().getHostAddress() + ":" + client.getPort());
                try {
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(client.getInputStream()));
                    String line;
                    while ((line = in.readLine()) != null) {
                        System.out.println(line);
                    }
                } catch (IOException e) {
                    System.out.println("FAIL" + e.getMessage());
                }
                System.out.println("socket is closed " + client.getInetAddress().getHostAddress() + ":" + client.getPort());
            }
        }).start();
    }
}
