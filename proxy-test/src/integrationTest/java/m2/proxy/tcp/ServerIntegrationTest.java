package m2.proxy.tcp;

import com.google.protobuf.ByteString;
import m2.proxy.executors.ServiceBaseExecutor;
import m2.proxy.proto.MessageOuterClass.Message;
import m2.proxy.proto.MessageOuterClass.RequestType;
import m2.proxy.tcp.handlers.ConnectionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ServerIntegrationTest {

    final TcpBaseServerBase server;
    final Random rnd = new Random();

    public static class TcpBaseClient extends TcpBaseClientBase {
        private static final Logger log = LoggerFactory.getLogger( TcpBaseClient.class );

        public TcpBaseClient(String clientId, int ServerPort, String localport) {
            super( clientId, "127.0.0.1", ServerPort, localport );
        }

        @Override
        public ConnectionHandler setConnectionHandler() {

            log.info( "set client handler" );
            return new ConnectionHandler() {
                @Override
                protected void onMessageIn(Message m) { }
                @Override
                protected void onMessageOut(Message m) { }
                @Override
                protected void onConnect(String ClientId, String remoteAddress) { }
                @Override
                protected void onDisconnect(String ClientId) { }
                @Override
                public void onRequest(long sessionId, long requestId, RequestType type, String destination, ByteString requestMessage) {
                    try {
                        Thread.sleep( new Random().nextInt( 2000 ) );
                        if (type == RequestType.PLAIN || type == RequestType.HTTP) {
                            reply( sessionId, requestId, type, requestMessage );
                        } else {
                            reply(
                                    sessionId,
                                    requestId,
                                    RequestType.NONE,
                                    null
                                 );
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException( e );
                    }
                }
            };
        }

    }

    public ServerIntegrationTest() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance( "RSA" );
        generator.initialize( 2048 );
        KeyPair rsaKey = generator.generateKeyPair();
        server = new TcpBaseServerBase( 4000, "", rsaKey ) {
            @Override
            public ConnectionHandler setConnectionHandler() {
                return new ConnectionHandler() {
                    @Override
                    protected void onMessageIn(Message m) { }
                    @Override
                    protected void onMessageOut(Message m) { }
                    @Override
                    protected void onConnect(String ClientId, String remoteAddress) { }
                    @Override
                    protected void onDisconnect(String ClientId) { }
                    @Override
                    protected void onRequest(long sessionId, long requestId, RequestType type, String address, ByteString request) {

                    }
                };
            }
        };

    }

    @BeforeEach
    void init() {
        server.start();
    }

    @AfterEach
    void end() { server.stop(); }

    @Test
    void server_start_stop() throws InterruptedException {

        Thread.sleep( 1000 * 5 );
        server.stop();
        Thread.sleep( 1000 * 10 );
        server.start();
        Thread.sleep( 1000 * 10 );

    }

    @Test
    void server_client_start_stop() throws InterruptedException {

        TcpBaseClient client1 = new TcpBaseClient( "leif", 4000, "" );
        client1.start();
        Thread.sleep( 1000 * 5 );
        server.stop();
        Thread.sleep( 1000 * 15 );
        server.start();
        Thread.sleep( 1000 * 30 );
        client1.stop();
        Thread.sleep( 1000 * 20 );
        client1.start();
        Thread.sleep( 1000 * 30 );
        client1.stop();
        Thread.sleep( 1000 * 30 );


    }

    @Test
    void one_client() throws InterruptedException {

        TcpBaseClient client1 = new TcpBaseClient( "leif2", 4000, "" );
        client1.start();
        Thread.sleep( 1000 * 30 );
        client1.stop();

    }

    @Test
    void one_client_big_message() throws InterruptedException {

        TcpBaseClient client1 = new TcpBaseClient( "leif2", 4000, "" );
        client1.start();
        Thread.sleep( 1000 );

        byte[] b = new byte[ 1000000 ];
        rnd.nextBytes( b );
        client1.getClients().forEach( (k, s) -> s.getHandler().sendRawMessage( b ) );

        Thread.sleep( 1000 * 30 );
        client1.stop();

    }

    @Test
    void many_clients() throws InterruptedException {

        TcpBaseClient client1 = new TcpBaseClient( "leif1", 4000, "localhost" );
        TcpBaseClient client2 = new TcpBaseClient( "leif2", 4000, "localhost" );
        TcpBaseClient client3 = new TcpBaseClient( "leif3", 4000, "localhost" );

        client1.start();
        client2.start();
        client3.start();

        Thread.sleep( 1000 * 15 );
        client1.stop();
        client2.stop();
        client3.stop();

    }

    public static class RandomClient extends TcpBaseClient {

        public RandomClient(String host, int port) {
            super( null, port, host );
        }

        @Override
        protected void startServices() {
            super.startServices();

            TcpBaseClient server = this;
            getExecutor().execute( () -> {
                while (this.isRunning()) {
                    int wait = rnd.nextInt( 200 );
                    server.getClients().forEach( (k, s) -> {
                        if (s.getHandler() != null) {
                            byte[] bytes = new byte[ rnd.nextInt( 2000000 ) + 200000 ];
                            rnd.nextBytes( bytes );
                            s.getHandler().sendRawMessage( bytes );
                        }
                        try {
                            Thread.sleep( wait );
                        } catch (InterruptedException ignored) {
                        }

                    } );

                }
            } );
        }
    }

    @Test
    void random_clients() throws InterruptedException {

        List<RandomClient> clients = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            RandomClient c = new RandomClient( "localhost", 4000 );
            clients.add( c );
            c.start();
        }
        Thread.sleep( 1000 * 30 );
        clients.forEach( ServiceBaseExecutor::stop );

    }

}
