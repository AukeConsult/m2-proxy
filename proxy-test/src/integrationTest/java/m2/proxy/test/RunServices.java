package m2.proxy.test;

import m2.proxy.server.ProxyServer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RunServices {


    @Test
    void spark_test() throws Exception {
        Factory.startSpark( 9999 );
        assertEquals(200,Factory.getRest(9999,"/hello"));
    }

    @Test
    void server_test()  {

        ProxyServer server = Factory.createServer( 9000, 9001 );
        server.start();
        assertTrue(server.isRunning());
        assertEquals(200,Factory.getRest(9000,"/local/hello"));
        assertEquals(404,Factory.getRest(9000,"/localX/hello"));
        server.stop();
        assertFalse(server.isRunning());

    }

    @Test
    void send_direct_test() throws Exception {

        Factory.startSpark(9999);

        ProxyServer server = Factory.createServer( 9000, 9001 );
        server.start();
        assertTrue(server.isRunning());
        assertEquals(200,Factory.getRestText(9000,"/spark/hello","how are you"));

        server.stop();
        assertFalse(server.isRunning());
        Factory.stopSpark(  );

    }

}
