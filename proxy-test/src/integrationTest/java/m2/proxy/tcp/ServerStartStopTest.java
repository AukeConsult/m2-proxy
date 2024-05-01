package m2.proxy.tcp;

import com.google.protobuf.ByteString;
import m2.proxy.executors.ServiceBaseExecutor;
import m2.proxy.proto.MessageOuterClass.Message;
import m2.proxy.proto.MessageOuterClass.MessageType;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServerStartStopTest {
    private static final Logger log = LoggerFactory.getLogger( ServerStartStopTest.class );

    final TcpServer tcpServer;
    public AtomicLong outBytes = new AtomicLong();
    public AtomicLong inBytes = new AtomicLong();
    public AtomicInteger outMessages = new AtomicInteger();
    public AtomicInteger inMessages = new AtomicInteger();

    final Random rnd = new Random();

    public static class TcpClient extends m2.proxy.tcp.TcpClient {
        private static final Logger log = LoggerFactory.getLogger( TcpClient.class );

        public AtomicLong outBytes = new AtomicLong();
        public AtomicLong inBytes = new AtomicLong();
        public AtomicInteger outMessages = new AtomicInteger();
        public AtomicInteger inMessages = new AtomicInteger();

        public TcpClient(String clientId, int ServerPort, String localport) {
            super( clientId, "127.0.0.1", ServerPort, localport );
        }

        @Override protected boolean onCheckAccess(String accessPath, String remoteAddress, String accessToken, String agent) {
            return true;
        }
        @Override protected Optional<String> onSetAccess(String userId, String passWord, String remoteAddress, String accessToken, String agent) {
            return Optional.of( getMyId()+"Key");
        }
        @Override
        public ConnectionHandler setConnectionHandler() {

            log.debug( "client handler" );
            return new ConnectionHandler() {
                @Override protected void onMessageIn(Message m) {
                    //log.info( "from {} -> {}", getRemoteClientId(),m.getType() );
                    if(m.getType() == MessageType.RAW_MESSAGE) {
                        inMessages.incrementAndGet();
                        inBytes.addAndGet( m.getSubMessage().toByteArray().length );
                    }
                }
                @Override protected void onMessageOut(Message m) {
                    //log.info( "to {} -> {}", getRemoteClientId(),m.getType() );
                    if(m.getType() == MessageType.RAW_MESSAGE) {
                        outMessages.incrementAndGet();
                        outBytes.addAndGet( m.getSubMessage().toByteArray().length );
                    }
                }
                @Override protected void onConnect(String ClientId, String remoteAddress) { }
                @Override protected void onDisonnect(String ClientId, String remoteAddress) { }
                @Override public void onRequest(long sessionId, long requestId, RequestType type, String destination, ByteString requestMessage) {
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

    public ServerStartStopTest() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance( "RSA" );
        generator.initialize( 2048 );
        KeyPair rsaKey = generator.generateKeyPair();
        tcpServer = new TcpServer( 4000, "", rsaKey ) {
            @Override
            public ConnectionHandler setConnectionHandler() {
                return new ConnectionHandler() {
                    @Override protected void onMessageIn(Message m) {
                        //log.info( "from {} -> {}", getRemoteClientId(),m.getType() );
                        if(m.getType()== MessageType.RAW_MESSAGE) {
                            inMessages.incrementAndGet();
                            inBytes.addAndGet( m.getSubMessage().toByteArray().length );
                            sendRawMessage( m.getSubMessage().toByteArray() );
                        }
                    }
                    @Override protected void onMessageOut(Message m) {
                        //log.info( "to {} -> {}", getRemoteClientId(),m.getType() );
                        if(m.getType()== MessageType.RAW_MESSAGE) {
                            outMessages.incrementAndGet();
                            outBytes.addAndGet( m.getSubMessage().toByteArray().length );
                            if(inMessages.get()!=outMessages.get()) {
                                log.info( "ERROR out", getRemoteClientId(),m.getType() );
                            }
                        }
                    }
                    @Override protected void onConnect(String ClientId, String remoteAddress) { }
                    @Override protected void onDisonnect(String ClientId, String remoteAddress) { }
                    @Override protected void onRequest(long sessionId, long requestId, RequestType type, String address, ByteString request) { }
                };
            }
        };

    }

    @BeforeEach
    void init() {
        outBytes.set(0);
        inBytes.set(0);
        outMessages.set(0);
        inMessages.set(0);
        tcpServer.start();
    }

    @AfterEach
    void end() { tcpServer.stop(); }

    @Test
    void server_start_stop() throws InterruptedException {

        Thread.sleep( 1000 * 5 );
        tcpServer.stop();
        Thread.sleep( 1000 * 10 );
        tcpServer.start();
        Thread.sleep( 1000 * 10 );

    }

    @Test
    void server_client_start_stop() throws InterruptedException {

        TcpClient client1 = new TcpClient( "leif", 4000, "" );
        client1.start();
        Thread.sleep( 1000 * 5 );
        tcpServer.stop();
        Thread.sleep( 1000 * 15 );
        tcpServer.start();
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

        TcpClient client1 = new TcpClient( "leif1", 4000, "" );
        client1.startWaitConnect(Duration.ofSeconds( 30 ));
        assertTrue(client1.isRunning() && client1.isConnected());
        Thread.sleep( 1000 * 15 );
        client1.stop();

    }

    @Test
    void one_client_big_messages() throws InterruptedException {

        log.info( "one_client_big_messages" );

        TcpClient client1 = new TcpClient( "leif1", 4000, "" );
        client1.startWaitConnect(Duration.ofSeconds( 30 ));
        assertTrue(client1.isRunning() && client1.isConnected());

        long start = System.currentTimeMillis();
        while(System.currentTimeMillis()-start<10000) {
            byte[] bytes = new byte[ rnd.nextInt( 200000 ) + 2000 ];
            rnd.nextBytes( bytes );
            client1.getTcpServers().forEach( (k, s) -> s.getHandler().sendRawMessage( bytes ) );
            Thread.sleep( 1 );
        }
        Thread.sleep( 1000 );
        client1.stop();

        log.info( "{} -> Bytes out: {}, in: {}", client1.getMyId(), client1.outBytes.get(),client1.inBytes.get());
        log.info( "{} -> Messages out: {}, in: {}", client1.getMyId(), client1.outMessages.get(),client1.inMessages.get());

        log.info( "Server -> Bytes, in: {}, out: {}", inBytes.get(),outBytes.get());
        log.info( "Server -> Messages, in: {}, out: {}", inMessages.get(),outMessages.get());

        assertEquals(client1.outMessages.get(),client1.inMessages.get());

        assertTrue(client1.inBytes.get()>0 && client1.outBytes.get()>0);
        assertEquals(client1.outBytes.get(),client1.inBytes.get());

    }

    @Test
    void many_clients_start() throws InterruptedException {

        log.info( "many_clients" );

        TcpClient client1 = new TcpClient( "leif1", 4000, "localhost" );
        TcpClient client2 = new TcpClient( "leif2", 4000, "localhost" );
        TcpClient client3 = new TcpClient( "leif3", 4000, "localhost" );

        client1.startWaitConnect( Duration.ofSeconds( 10 ) );
        client2.startWaitConnect( Duration.ofSeconds( 10 ) );
        client3.startWaitConnect( Duration.ofSeconds( 10 ) );

        log.info( "many_clients started" );

        assertTrue(client1.isReady());
        assertTrue(client2.isReady());
        assertTrue(client3.isReady());
        assertTrue(client2.isRunning() && client2.isConnected());
        assertTrue(client3.isRunning() && client3.isConnected());

        Thread.sleep( 1000 * 10 );
        client1.stop();
        client2.stop();
        client3.stop();

        log.info( "client 1, out: {}, in: {}", client1.outBytes.get(),client1.inBytes.get());
        log.info( "client 2, out: {}, in: {}", client2.outBytes.get(),client2.inBytes.get());
        log.info( "client 3, out: {}, in: {}", client3.outBytes.get(),client3.inBytes.get());

        assertTrue(client1.inBytes.get()>0 && client1.outBytes.get()>0);
        assertTrue(client2.inBytes.get()>0 && client2.outBytes.get()>0);
        assertTrue(client3.inBytes.get()>0 && client3.outBytes.get()>0);

        assertEquals(client1.inBytes.get(),client1.outBytes.get());
        assertEquals(client2.inBytes.get(),client2.outBytes.get());
        assertEquals(client3.inBytes.get(),client3.outBytes.get());

    }

    public static class RandomClient extends TcpClient {

        public RandomClient(String serverHost, int serverPort) {
            super( null, serverPort, serverHost );
        }

        @Override
        protected void startServices() {
            super.startServices();

            TcpClient client = this;
            getExecutor().execute( () -> {
                while (this.isRunning()) {
                    int wait = rnd.nextInt( 200 );
                    client.getTcpServers().forEach( (k, s) -> {
                        if (s.getHandler() != null && s.getHandler().isConnected()) {
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

        log.info( "random_clients" );
        List<RandomClient> tcpClients = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            RandomClient c = new RandomClient( "localhost", 4000 );
            tcpClients.add( c );
            c.start();
        }
        Thread.sleep( 1000 * 30 );
        tcpClients.forEach( c -> log.info("{} -> in: {}, out: {}",c.myId,c.inBytes.get(),c.outBytes.get()) );

        tcpClients.forEach( c -> assertEquals(c.inBytes.get(),c.outBytes.get()) );
        tcpClients.forEach( ServiceBaseExecutor::stop );

    }

}
