package m2.proxy;

import m2.proxy.executors.*;
import m2.proxy.tcp.TcpBaseClient;
import m2.proxy.tcp.TcpBaseServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ServerIntegrationTest {


    final TcpBaseServer server;
    final Random rnd = new Random();

    public ServerIntegrationTest() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair rsaKey = generator.generateKeyPair();
        server = new TcpBaseServer(4000, "", rsaKey);
    }

    @BeforeEach
    void init() {
        server.start();
    }

    @AfterEach
    void end() {
        server.stop();
    }

    @Test
    void one_client() throws InterruptedException {

        TcpBaseClient client1 = new TcpBaseClient("localhost",4000, "");
        client1.start();
        Thread.sleep(1000*30);
        client1.stop();

    }

    @Test
    void one_client_big_message() throws InterruptedException {

        TcpBaseClient client1 = new TcpBaseClient("localhost",4000, "");
        client1.start();
        Thread.sleep(1000);

        byte[] b = new byte[1000000];
        rnd.nextBytes(b);
        client1.getHandler().sendMessage(new String(b));

        Thread.sleep(1000*30);
        client1.stop();

    }


    @Test
    void many_clients() throws InterruptedException {

        TcpBaseClient client1 = new TcpBaseClient("localhost",4000, "");
        TcpBaseClient client2 = new TcpBaseClient("localhost",4000, "");
        TcpBaseClient client3 = new TcpBaseClient("localhost",4000, "");

        client1.start();
        client2.start();
        client3.start();

        Thread.sleep(1000*15);
        client1.stop();
        client2.stop();
        client3.stop();

    }

    public static class RandomClient extends TcpBaseClient {

        public RandomClient(String host, int port) {
            super(host, port, "");
        }

        @Override
        protected void startServices() {
            super.startServices();

            TcpBaseClient server = this;
            getExecutor().execute(() -> {
                while(this.isRunning()) {
                    int wait = rnd.nextInt(200);
                    if(server.getHandler()!=null) {
                        byte[] bytes = new byte[rnd.nextInt(200000)+10];
                        rnd.nextBytes(bytes);
                        server.getHandler().sendMessage(new String(bytes));
                    }
                    try {
                        Thread.sleep(wait);
                    } catch (InterruptedException ignored) {
                    }
                }
            });
        }
    }

    @Test
    void random_clients() throws InterruptedException {

        List<RandomClient> clients= new ArrayList<>();
        for(int i = 0;i<5;i++) {
            RandomClient c = new RandomClient("localhost", 4000);
            clients.add(c);
            c.start();
        }
        Thread.sleep(1000*30);
        clients.forEach(ServiceBaseExecutor::stop);

    }

}
