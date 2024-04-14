package m2.proxy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ServerTest {

    NettyServer server = new NettyServer(4000);

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
        Thread.sleep(1000*60);
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

        Thread.sleep(1000*60);
        client1.stop();
        client2.stop();
        client3.stop();

    }

}
