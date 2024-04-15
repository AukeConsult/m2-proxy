package m2.proxy;

import m2.proxy.executors.ServiceBaseExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ServerTest {


    final NettyServer server;
    final Random rnd = new Random();

    public ServerTest() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair rsaKey = generator.generateKeyPair();
        server = new NettyServer(4000, rsaKey);
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

        NettyClient client1 = new NettyClient("localhost",4000);
        client1.start();
        Thread.sleep(1000*10);
        client1.stop();

    }

    @Test
    void one_client_big_message() throws InterruptedException {

        NettyClient client1 = new NettyClient("localhost",4000);
        client1.start();
        Thread.sleep(10000);

        byte[] b = new byte[1000000];
        rnd.nextBytes(b);
        client1.getHandler().sendMessage(new String(b));

        Thread.sleep(1000*10);
        client1.stop();

    }


    @Test
    void many_clients() throws InterruptedException {

        NettyClient client1 = new NettyClient("localhost",4000);
        NettyClient client2 = new NettyClient("localhost",4000);
        NettyClient client3 = new NettyClient("localhost",4000);

        client1.start();
        client2.start();
        client3.start();

        Thread.sleep(1000*15);
        client1.stop();
        client2.stop();
        client3.stop();

    }

    public class RandomClient extends NettyClient {

        public RandomClient(String host, int port) {
            super(host, port);
        }

        @Override
        protected void startServices() {
            super.startServices();

            NettyClient server = this;
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
                    } catch (InterruptedException e) {
                    }
                }
            });
        }
    }

    @Test
    void random_clients() throws InterruptedException {

        List<RandomClient> clients= new ArrayList<>();
        for(int i = 0;i<5;i++) {
            RandomClient c = new RandomClient("localhost",4000);
            clients.add(c);
            c.start();
        }
        Thread.sleep(1000*30);
        clients.forEach(ServiceBaseExecutor::stop);

    }

}
