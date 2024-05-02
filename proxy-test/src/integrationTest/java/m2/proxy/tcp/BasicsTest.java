package m2.proxy.tcp;

import com.google.protobuf.ByteString;
import m2.proxy.executors.ServiceBaseExecutor;
import m2.proxy.proto.MessageOuterClass.Message;
import m2.proxy.proto.MessageOuterClass.MessageType;
import m2.proxy.proto.MessageOuterClass.RequestType;
import m2.proxy.tcp.handlers.ConnectionHandler;
import m2.proxy.tcp.server.TcpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class BasicsTest {
    private static final Logger log = LoggerFactory.getLogger( BasicsTest.class );

    final TcpServer tcpServer;
    public AtomicLong outBytes = new AtomicLong();
    public AtomicLong inBytes = new AtomicLong();
    public AtomicInteger outMessages = new AtomicInteger();
    public AtomicInteger inMessages = new AtomicInteger();

    final Random rnd = new Random();

    public static class TcpClient extends m2.proxy.tcp.client.TcpClient {
        private static final Logger log = LoggerFactory.getLogger( TcpClient.class );

        public AtomicLong outBytes = new AtomicLong();
        public AtomicLong inBytes = new AtomicLong();
        public AtomicInteger outMessages = new AtomicInteger();
        public AtomicInteger inMessages = new AtomicInteger();
        public AtomicInteger inDisconnect = new AtomicInteger();

        public TcpClient(String clientId, int ServerPort, String localport) {
            super( clientId, "127.0.0.1", ServerPort, localport );
        }

        @Override protected boolean onCheckAccess(String accessPath, String remoteAddress, String accessToken, String agent) {
            return true;
        }
        @Override protected Optional<String> onSetAccess(String userId, String passWord, String remoteAddress, String accessToken, String agent) {
            return Optional.of( myId() + "Key" );
        }
        @Override
        public ConnectionHandler setConnectionHandler() {

            log.debug( "client handler" );
            return new ConnectionHandler() {
                @Override protected void onMessageIn(Message m) {
                    //log.info( "from {} -> {}", getRemoteClientId(),m.getType() );
                    if (m.getType() == MessageType.RAW_MESSAGE || m.getType() == MessageType.MESSAGE) {
                        inMessages.incrementAndGet();
                        inBytes.addAndGet( m.getSubMessage().toByteArray().length );
                    }
                    if (m.getType() == MessageType.DISCONNECT) {
                        log.info( "{} -> got disconnect from {}", myId(), getRemoteClientId() );
                        inDisconnect.incrementAndGet();
                    }
                }
                @Override protected void onMessageOut(Message m) {
                    //log.info( "to {} -> {}", getRemoteClientId(),m.getType() );
                    if (m.getType() == MessageType.RAW_MESSAGE || m.getType() == MessageType.MESSAGE) {
                        outMessages.incrementAndGet();
                        outBytes.addAndGet( m.getSubMessage().toByteArray().length );
                    }
                }
                @Override protected void onConnect(String ClientId, String remoteAddress) { }
                @Override protected void onDisonnect(String ClientId, String remoteAddress) {
                    log.info( "{} -> disconnect from: {}", myId(), getRemoteClientId() );
                }
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

    public BasicsTest() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance( "RSA" );
        generator.initialize( 2048 );
        KeyPair rsaKey = generator.generateKeyPair();
        tcpServer = new TcpServer( 4000, "", rsaKey ) {
            @Override
            public ConnectionHandler setConnectionHandler() {
                return new ConnectionHandler() {
                    @Override protected void onMessageIn(Message m) {
                        //log.info( "from {} -> {}", getRemoteClientId(),m.getType() );
                        if (m.getType() == MessageType.RAW_MESSAGE || m.getType() == MessageType.MESSAGE) {
                            inMessages.incrementAndGet();
                            inBytes.addAndGet( m.getSubMessage().toByteArray().length );
                            if (m.getType() == MessageType.RAW_MESSAGE) {
                                sendRawMessage( m.getSubMessage().toByteArray() );
                            } else {
                                sendMessage( m.getSubMessage().toStringUtf8() );
                            }
                        }
                    }
                    @Override protected void onMessageOut(Message m) {
                        //log.info( "to {} -> {}", getRemoteClientId(),m.getType() );
                        if (m.getType() == MessageType.RAW_MESSAGE || m.getType() == MessageType.MESSAGE) {
                            outMessages.incrementAndGet();
                            outBytes.addAndGet( m.getSubMessage().toByteArray().length );
                        }
                        if (m.getType() == MessageType.DISCONNECT) {
                            log.info( "{} -> Disconnect: {}", myId(), getRemoteClientId() );
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
        outBytes.set( 0 );
        inBytes.set( 0 );
        outMessages.set( 0 );
        inMessages.set( 0 );
        tcpServer.start();
        assertTrue( tcpServer.isRunning() );
    }

    @AfterEach
    void end() { tcpServer.stop(); }

    @Test
    void server_start_stop() throws InterruptedException {

        Thread.sleep( 1000 * 5 );
        assertTrue( tcpServer.isRunning() );
        tcpServer.stop();
        assertFalse( tcpServer.isRunning() );
        Thread.sleep( 1000 * 10 );
        tcpServer.start();
        assertTrue( tcpServer.isRunning() );
        Thread.sleep( 1000 * 10 );
    }

    @Test
    void server_disconnect() throws InterruptedException {

        TcpClient client1 = new TcpClient( "leif", 4000, "" );
        client1.setReconnectTimeSeconds( 10 );

        client1.startWaitConnect( Duration.ofSeconds( 2 ) );
        assertTrue( client1.isConnected() );
        assertEquals( 1, client1.getTcpRemoteServers().size() );
        assertEquals( 1, client1.activeClients.size() );

        assertEquals( 1, tcpServer.activeClients.size() );
        tcpServer.stop();
        Thread.sleep( 1000 * 3 );

        assertEquals( 0, tcpServer.activeClients.size() );
        assertEquals( 0, client1.activeClients.size() );
        assertEquals( 1, client1.getTcpRemoteServers().size() );
        assertFalse( client1.isConnected() );

        // client try to reconnect
        Thread.sleep( 1000 * 30 );
        assertEquals( 0, client1.activeClients.size() );
        assertEquals( 1, client1.getTcpRemoteServers().size() );
    }

    @Test
    void server_stop_client_try_reconnect() throws InterruptedException {

        tcpServer.stop();

        List<TcpClient> clients = new ArrayList<>( Arrays.asList(
                new TcpClient( "leif1", 4000, "" ),
                new TcpClient( "leif2", 4000, "" ),
                new TcpClient( "leif3", 4000, "" ),
                new TcpClient( "leif4", 4000, "" ),
                new TcpClient( "leif5", 4000, "" ),
                new TcpClient( "leif6", 4000, "" ),
                new TcpClient( "leif7", 4000, "" ),
                new TcpClient( "leif8", 4000, "" ),
                new TcpClient( "leif9", 4000, "" ),
                new TcpClient( "leif10", 4000, "" )
        ) );

        clients.forEach( c -> {

            tcpServer.getTaskPool().execute( () -> {
                final TcpClient client = c;
                client.setReconnectTimeSeconds( 10 );
                assertEquals( 0, client.getTcpRemoteServers().size() );

                client.startWaitConnect( Duration.ofSeconds( 2 ) );
                assertEquals( 1, client.getTcpRemoteServers().size() );
                assertFalse( client.getTcpRemoteServers().get( 0 ).connected.get() );
                assertFalse( client.isConnected() );
                assertEquals( 1, client.getTcpRemoteServers().size() );
                assertEquals( 0, client.activeClients.size() );
            } );
        } );

        Thread.sleep( 30 * 1000 );
        clients.forEach( client -> {

            assertEquals( 1, client.getTcpRemoteServers().size() );
            assertEquals( 5, client.getTcpRemoteServers().get( 0 ).connectionErrors.get(), 3 );
            assertFalse( client.getTcpRemoteServers().get( 0 ).connected.get() );
        } );

        tcpServer.start();
        Thread.sleep( 10 * 1000 );

        clients.forEach( client -> {
            assertEquals( 1, client.getTcpRemoteServers().size() );
            assertTrue( client.getTcpRemoteServers().get( 0 ).connected.get() );
            assertTrue( client.isConnected() );
        } );

        assertEquals( 10, tcpServer.getClientHandles().size() );
        assertEquals( 10, tcpServer.getActiveClients().size() );

        tcpServer.stop();
        Thread.sleep( 10 * 1000 );

        clients.forEach( client -> {
            assertEquals( 1, client.getTcpRemoteServers().size() );
            assertFalse( client.getTcpRemoteServers().get( 0 ).connected.get() );
            assertFalse( client.isConnected() );
        } );

        assertEquals( 0, tcpServer.getClientHandles().size() );
        assertEquals( 0, tcpServer.getActiveClients().size() );
    }

    @Test
    void client_stop_client_try_reconnect() throws InterruptedException {

        TcpClient client1 = new TcpClient( "leif", 4000, "" );
        client1.setReconnectTimeSeconds( 10 );
        assertEquals( 0, client1.getTcpRemoteServers().size() );

        client1.startWaitConnect( Duration.ofSeconds( 2 ) );
        assertEquals( 1, client1.getTcpRemoteServers().size() );
        Thread.sleep( 5 * 1000 );
        assertTrue( client1.getTcpRemoteServers().get( 0 ).connected.get() );

        assertEquals( 1, tcpServer.getClientHandles().size() );
        assertEquals( 1, tcpServer.getActiveClients().size() );

        client1.stop();
        assertFalse( client1.isConnected() );
        assertEquals( 0, client1.getTcpRemoteServers().size() );
        assertEquals( 0, client1.activeClients.size() );
        Thread.sleep( 5 * 1000 );
        assertEquals( 0, tcpServer.getClientHandles().size() );
        assertEquals( 0, tcpServer.getActiveClients().size() );

        client1.startWaitConnect( Duration.ofSeconds( 2 ) );
        assertEquals( 1, client1.getTcpRemoteServers().size() );
        Thread.sleep( 5 * 1000 );
        assertTrue( client1.getTcpRemoteServers().get( 0 ).connected.get() );

        assertEquals( 1, tcpServer.getClientHandles().size() );
        assertEquals( 1, tcpServer.getActiveClients().size() );

        Thread.sleep( 5 * 1000 );

        client1.stop();
        assertFalse( client1.isConnected() );
        assertEquals( 0, client1.getTcpRemoteServers().size() );
        assertEquals( 0, client1.activeClients.size() );
        Thread.sleep( 5 * 1000 );
        assertEquals( 0, tcpServer.getClientHandles().size() );
        assertEquals( 0, tcpServer.getActiveClients().size() );
    }

    @Test
    void one_client() throws InterruptedException {

        TcpClient client1 = new TcpClient( "leif1", 4000, "" );
        client1.startWaitConnect( Duration.ofSeconds( 30 ) );
        assertTrue( client1.isRunning() && client1.isConnected() );
        Thread.sleep( 1000 * 15 );
        client1.stop();
    }

    @Test
    void one_client_big_raws_messages() throws InterruptedException {

        log.info( "one_client_big_messages" );

        TcpClient client1 = new TcpClient( "leif1", 4000, "" );
        client1.startWaitConnect( Duration.ofSeconds( 30 ) );
        assertTrue( client1.isRunning() && client1.isConnected() );

        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 10000) {
            byte[] bytes = new byte[ rnd.nextInt( 200000 ) + 2000 ];
            rnd.nextBytes( bytes );
            client1.getTcpRemoteServers().forEach( s -> s.sendRawMessage( bytes ) );
            Thread.sleep( 1 );
        }
        Thread.sleep( 1000 );
        client1.stop();

        log.info( "{} -> Bytes out: {}, in: {}", client1.myId(), client1.outBytes.get(), client1.inBytes.get() );
        log.info( "{} -> Messages out: {}, in: {}", client1.myId(), client1.outMessages.get(), client1.inMessages.get() );

        log.info( "Server -> Bytes, in: {}, out: {}", inBytes.get(), outBytes.get() );
        log.info( "Server -> Messages, in: {}, out: {}", inMessages.get(), outMessages.get() );

        assertEquals( client1.outMessages.get(), client1.inMessages.get() );

        assertTrue( client1.inBytes.get() > 0 && client1.outBytes.get() > 0 );
        assertEquals( client1.outBytes.get(), client1.inBytes.get() );
    }

    @Test
    void one_client_big_messages() throws InterruptedException {

        log.info( "one_client_big_messages" );

        TcpClient client1 = new TcpClient( "leif1", 4000, "" );
        client1.startWaitConnect( Duration.ofSeconds( 30 ) );
        assertTrue( client1.isRunning() && client1.isConnected() );

        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 10000) {
            byte[] bytes = new byte[ rnd.nextInt( 200000 ) + 2000 ];
            rnd.nextBytes( bytes );
            client1.getTcpRemoteServers().forEach( s -> s.sendMessage( new String( bytes ) ) );
            Thread.sleep( 1 );
        }
        Thread.sleep( 1000 );
        client1.stop();

        log.info( "{} -> Bytes out: {}, in: {}", client1.myId(), client1.outBytes.get(), client1.inBytes.get() );
        log.info( "{} -> Messages out: {}, in: {}", client1.myId(), client1.outMessages.get(), client1.inMessages.get() );

        log.info( "Server -> Bytes, in: {}, out: {}", inBytes.get(), outBytes.get() );
        log.info( "Server -> Messages, in: {}, out: {}", inMessages.get(), outMessages.get() );

        assertEquals( client1.outMessages.get(), client1.inMessages.get() );

        assertTrue( client1.inBytes.get() > 0 && client1.outBytes.get() > 0 );
        assertEquals( client1.outBytes.get(), client1.inBytes.get() );
    }

    @Test
    void many_clients_send_message() throws InterruptedException {

        log.info( "many_clients" );

        List<TcpClient> clients = new ArrayList<>( Arrays.asList(
                new TcpClient( "leif1", 4000, "" ),
                new TcpClient( "leif2", 4000, "" ),
                new TcpClient( "leif3", 4000, "" ),
                new TcpClient( "leif4", 4000, "" ),
                new TcpClient( "leif5", 4000, "" ),
                new TcpClient( "leif6", 4000, "" ),
                new TcpClient( "leif7", 4000, "" ),
                new TcpClient( "leif8", 4000, "" ),
                new TcpClient( "leif9", 4000, "" ),
                new TcpClient( "leif10", 4000, "" )
        ) );

        clients.forEach( client -> client.startWaitConnect( Duration.ofSeconds( 10 ) ) );

        log.info( "many_clients started" );

        clients.forEach( client -> assertTrue( client.isReady() ) );
        clients.forEach( client -> assertTrue( client.isRunning() && client.isConnected() ) );

        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 10000) {
            clients.forEach( client -> {
                byte[] bytes = new byte[ rnd.nextInt( 200 ) + 20 ];
                rnd.nextBytes( bytes );
                client.getTcpRemoteServers().get( 0 ).sendRawMessage( bytes );
            } );
            Thread.sleep( 1 );
        }
        Thread.sleep( 1000 );

        clients.forEach( client -> client.stop() );

        clients.forEach( client -> {
            log.info( "{} -> out: {}, in: {}", client.myId, client.outBytes.get(), client.inBytes.get() );
            assertTrue( client.inBytes.get() > 0 && client.outBytes.get() > 0 );
            assertEquals( client.inBytes.get(), client.outBytes.get() );
        } );
    }

    public static class RandomClient extends TcpClient {

        public RandomClient(String serverHost, int serverPort) {
            super( null, serverPort, serverHost );
        }

        protected void runTest() {

            TcpClient client = this;
            long start = System.currentTimeMillis();
            while (this.isRunning() && System.currentTimeMillis() - start < 30000) {
                int wait = rnd.nextInt( 1000 ) + 100;
                client.getTcpRemoteServers().forEach( s -> {
                    if (s.getHandler() != null && s.getHandler().isConnected()) {
                        byte[] bytes = new byte[ rnd.nextInt( 100000 ) + 20000 ];
                        rnd.nextBytes( bytes );
                        s.getHandler().sendRawMessage( bytes );
                    }
                    try {
                        Thread.sleep( wait );
                    } catch (InterruptedException ignored) {
                    }
                } );
            }
            try {
                Thread.sleep( 1000 * 10 );
            } catch (InterruptedException e) { }
            this.stop();
        }
    }

    @Test
    void random_many_clients() throws InterruptedException {

        log.info( "random_clients" );
        List<RandomClient> tcpClients = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            RandomClient c = new RandomClient( "localhost", 4000 );
            tcpClients.add( c );
            log.info( "initiate client: {}, {}", c.myId(), i );
        }

        log.info( "Start clients: {}", tcpClients.size() );

        Executor start_task = Executors.newFixedThreadPool( 5 );
        ThreadPoolExecutor execute_task = ( ThreadPoolExecutor ) Executors.newFixedThreadPool( 110 );

        tcpClients.forEach( c -> {
            start_task.execute( () -> {

                final RandomClient client = c;
                log.debug( "{} -> client prepeared", client.myId() );

                client.setReconnectTimeSeconds( 2 );
                client.startWaitConnect( Duration.ofSeconds( 10 ) );
                if (client.isReady()) {
                    execute_task.execute( () -> {
                        client.runTest();
                    } );
                    log.debug( "{} -> client started", client.myId() );
                } else {
                    log.warn( "{} -> client NOT started", client.myId() );
                }
            } );
        } );
        Thread.sleep( 1000 );

        log.info( "All started threads: {}", execute_task.getActiveCount() );

        while (execute_task.getActiveCount() > 0) {
            log.info( "Treads still running: {}", execute_task.getActiveCount() );
            Thread.sleep( 1000 * 5 );
        } ;

        Thread.sleep( 1000 * 10 );
        tcpClients.forEach( c -> log.info( "{} -> out: {}, in: {}, diff: {}",
                c.myId,
                c.outBytes.get(),
                c.inBytes.get(),
                c.outBytes.get() - c.inBytes.get() )
        );

        Thread.sleep( 1000 * 10 );

        tcpClients.forEach( c -> assertEquals( c.outBytes.get(), c.inBytes.get() ) );
        tcpClients.forEach( ServiceBaseExecutor::stop );
    }
}
