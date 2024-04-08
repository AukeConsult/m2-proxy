package no.auke.m2.proxy;

import java.io.IOException;
import java.net.Socket;

public class ServSocket {
    Socket node_server;
    public ServSocket(String host, int port) throws IOException {
        node_server = new Socket(host, port);
    }
    public void send(byte[] bytes) {

    }
}
