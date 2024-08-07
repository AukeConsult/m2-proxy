package m2.proxy.tcp;

import com.google.protobuf.ByteString;
import m2.proxy.executors.ServiceBaseExecutor;
import m2.proxy.proto.MessageOuterClass.Message;
import m2.proxy.proto.MessageOuterClass.MessageType;
import m2.proxy.proto.MessageOuterClass.RequestType;
import m2.proxy.tcp.handlers.ConnectionHandler;
import m2.proxy.tcp.server.TcpServer;
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

    TcpServer tcpServer;
    public AtomicLong outBytes = new AtomicLong();
    public AtomicLong inBytes = new AtomicLong();
    public AtomicInteger outMessages = new AtomicInteger();
    public AtomicInteger inMessages = new AtomicInteger();
    final Random rnd = new Random();

    static KeyPair rsaKeyClient;
    static KeyPair getRsaKey()  {
        try {
            if(rsaKeyClient==null) {
                KeyPairGenerator generator = KeyPairGenerator.getInstance( "RSA" );
                generator.initialize( 2048 );
                rsaKeyClient = generator.generateKeyPair();
            }
        } catch (NoSuchAlgorithmException ignore) {
        }
        return rsaKeyClient;
    }

    static KeyPair rsaKeyServer;
    static KeyPair getServerRsaKey()  {
        try {
            if(rsaKeyServer==null) {
                KeyPairGenerator generator = KeyPairGenerator.getInstance( "RSA" );
                generator.initialize( 2048 );
                rsaKeyServer = generator.generateKeyPair();
            }
        } catch (NoSuchAlgorithmException ignore) {
        }
        return rsaKeyServer;
    }


    public static class TcpClient extends m2.proxy.tcp.client.TcpClient {
        private static final Logger log = LoggerFactory.getLogger( TcpClient.class );

        public AtomicLong outBytes = new AtomicLong();
        public AtomicLong inBytes = new AtomicLong();
        public AtomicInteger outMessages = new AtomicInteger();
        public AtomicInteger inMessages = new AtomicInteger();
        public AtomicInteger inDisconnect = new AtomicInteger();

        public TcpClient(String clientId, int ServerPort, String localport)  {
            super( clientId, "127.0.0.1", ServerPort, localport, getRsaKey() );
        }

        @Override protected boolean onCheckAccess(String accessPath, String remoteAddress, String accessToken, String agent) {
            return true;
        }
        @Override protected Optional<String> onSetAccess(String userId, String passWord, String remoteAddress, String accessToken, String agent) {
            return Optional.of( myId() + "Key" );
        }
        @Override
        public ConnectionHandler setConnectionHandler() {

            log.trace( "client handler" );
            return new ConnectionHandler() {
                @Override protected void handlerOnMessageIn(Message m) {
                    //log.info( "from {} -> {}", getRemoteClientId(),m.getType() );
                    if (m.getType() == MessageType.RAW_MESSAGE || m.getType() == MessageType.MESSAGE) {
                        inMessages.incrementAndGet();
                        inBytes.addAndGet( m.getSubMessage().toByteArray().length );
                    }
                    if (m.getType() == MessageType.DISCONNECT) {
                        inDisconnect.incrementAndGet();
                    }
                }
                @Override protected void handlerOnMessageOut(Message m) {
                    if (m.getType() == MessageType.RAW_MESSAGE || m.getType() == MessageType.MESSAGE) {
                        outMessages.incrementAndGet();
                        outBytes.addAndGet( m.getSubMessage().toByteArray().length );
                    }
                }
                @Override protected void handlerOnConnect(String ClientId, String remoteAddress) { }
                @Override protected void handlerOnDisonnect(String ClientId, String remoteAddress) {
                    log.trace( "{} -> disconnect from: {}", myId(), getRemoteClientId() );
                }
                @Override public void notifyOnRequest(long sessionId, long requestId, RequestType type, String destination, ByteString requestMessage) {
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

    public void startServer() throws NoSuchAlgorithmException, InterruptedException {

        stopServer();

        tcpServer = new TcpServer( 4000, "", getServerRsaKey() ) {
            @Override
            public ConnectionHandler setConnectionHandler() {
                return new ConnectionHandler() {
                    @Override protected void handlerOnMessageIn(Message m) {
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
                    @Override protected void handlerOnMessageOut(Message m) {
                        //log.info( "to {} -> {}", getRemoteClientId(),m.getType() );
                        if (m.getType() == MessageType.RAW_MESSAGE || m.getType() == MessageType.MESSAGE) {
                            outMessages.incrementAndGet();
                            outBytes.addAndGet( m.getSubMessage().toByteArray().length );
                        }
                        if (m.getType() == MessageType.DISCONNECT) {
                            log.info( "{} -> Disconnect: {}", myId(), getRemoteClientId() );
                        }
                    }
                    @Override protected void handlerOnConnect(String ClientId, String remoteAddress) { }
                    @Override protected void handlerOnDisonnect(String ClientId, String remoteAddress) { }
                    @Override protected void notifyOnRequest(long sessionId, long requestId, RequestType type, String address, ByteString request) { }
                };
            }
        };
        assertFalse( tcpServer.isRunning() );
        log.info("server init");

        outBytes.set( 0 );
        inBytes.set( 0 );
        outMessages.set( 0 );
        inMessages.set( 0 );

        assertEquals( 0, tcpServer.getActiveClients().size() );
        assertEquals( 0, tcpServer.getClientHandles().size() , 0);

        tcpServer.start();
        assertTrue( tcpServer.isRunning() );
        assertEquals( 0, tcpServer.getClientHandles().size() , 0);

        log.info("server started");

    }

    void stopServer() throws InterruptedException {

        if(tcpServer!=null && tcpServer.isServiceRunning()) {
            tcpServer.stop();
            assertEquals( 0, tcpServer.getClientHandles().size() , 0);
            assertEquals( 0, tcpServer.getActiveClients().size() );
            log.info("server stopped");
        }

    }

    @Test
    void server_start_stop() throws InterruptedException, NoSuchAlgorithmException {

        startServer();

        Thread.sleep( 1000 * 5 );
        assertTrue( tcpServer.isRunning() );
        tcpServer.stop();
        assertEquals( 0, tcpServer.getClientHandles().size() , 0);
        assertFalse( tcpServer.isRunning() );
        Thread.sleep( 1000 * 10 );
        tcpServer.start();
        assertTrue( tcpServer.isRunning() );
        Thread.sleep( 1000 * 10 );

        stopServer();
    }

    @Test
    void client_disconnect() throws InterruptedException, NoSuchAlgorithmException {

        startServer();

        TcpClient client1 = new TcpClient( "leif", 4000, "" );
        client1.setReconnectTimeSeconds( 5 );
        client1.startWaitConnect( Duration.ofSeconds( 5 ) );
        assertTrue( client1.isConnected() );
        Thread.sleep( 2000 );

        assertEquals( 1, client1.getTcpRemoteServers().size() );
        assertEquals( 1, client1.getActiveClients().size() );
        assertEquals( 1, tcpServer.getActiveClients().size() );
        Thread.sleep( 2000 );

        log.info("client stop");
        client1.stop();
        Thread.sleep( 5000 );

        assertEquals( 0, tcpServer.getClientHandles().size() );
        assertEquals( 0, tcpServer.getActiveClients().size() );
        assertEquals( 0, client1.getActiveClients().size() );
        assertEquals( 0, client1.getTcpRemoteServers().size() );
        assertFalse( client1.isConnected() );

        log.info("client start");
        client1.startWaitConnect( Duration.ofSeconds( 2 ) );
        Thread.sleep( 5000 );

        assertEquals( 1, client1.getTcpRemoteServers().size() );
        assertEquals( 1, client1.getActiveClients().size() );
        assertEquals( 1, tcpServer.getActiveClients().size() );
        Thread.sleep( 2000 );

        tcpServer.stop();

        Thread.sleep( 5000 );

        assertEquals( 0, tcpServer.getClientHandles().size() );
        assertEquals( 0, tcpServer.getActiveClients().size() );
        assertEquals( 0, client1.getActiveClients().size() );
        assertEquals( 1, client1.getTcpRemoteServers().size() );

        stopServer();


    }

    @Test
    void server_disconnect() throws InterruptedException, NoSuchAlgorithmException {

        startServer();

        TcpClient client1 = new TcpClient( "leif", 4000, "" );
        client1.setReconnectTimeSeconds( 5 );

        client1.startWaitConnect( Duration.ofSeconds( 2 ) );
        assertTrue( client1.isConnected() );
        Thread.sleep( 2000 );

        assertEquals( 1, client1.getTcpRemoteServers().size() );
        assertEquals( 1, client1.getActiveClients().size() );
        assertEquals( 1, tcpServer.getActiveClients().size() );
        Thread.sleep( 2000 );

        log.info("server stop");
        tcpServer.stop();
        Thread.sleep( 1000 * 5 );

        assertEquals( 0, tcpServer.getClientHandles().size() );
        assertEquals( 0, tcpServer.getActiveClients().size() );
        assertEquals( 0, client1.getActiveClients().size() );
        assertEquals( 1, client1.getTcpRemoteServers().size() );
        assertFalse( client1.isConnected() );

        // client try to reconnect
        Thread.sleep( 1000 * 15 );
        assertEquals( 0, client1.getActiveClients().size() );
        assertEquals( 1, client1.getTcpRemoteServers().size() );

        log.info("server start again");
        tcpServer.start();
        Thread.sleep( 10000 );
        assertEquals( 1, client1.getActiveClients().size() );
        assertEquals( 1, client1.getTcpRemoteServers().size() );
        assertTrue( client1.isConnected() );

        assertEquals( 1, tcpServer.getClientHandles().size() );
        assertEquals( 1, tcpServer.getActiveClients().size() );

        stopServer();


    }

    @Test
    void server_stopped_client_try_reconnect() throws InterruptedException, NoSuchAlgorithmException {

        startServer();

        tcpServer.stop();
        Thread.sleep( 2 * 1000 );

        assertEquals( 0, tcpServer.getClientHandles().size() );
        assertEquals( 0, tcpServer.getActiveClients().size() );

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
                c.setReconnectTimeSeconds( 1 );
                assertEquals( 0, c.getTcpRemoteServers().size() );

                c.startWaitConnect( Duration.ofSeconds( 5 ) );
                assertEquals( 1, c.getTcpRemoteServers().size() );
                assertFalse( c.getTcpRemoteServers().get( 0 ).connected.get() );
                assertFalse( c.isConnected() );
                assertEquals( 1, c.getTcpRemoteServers().size() );
                assertEquals( 0, c.getActiveClients().size() );
            } );
        } );

        Thread.sleep( 15 * 1000 );
        clients.forEach( client -> {
            assertEquals( 1, client.getTcpRemoteServers().size() );
            assertFalse( client.getTcpRemoteServers().get( 0 ).connected.get() );
        } );

        assertEquals( 0, tcpServer.getClientHandles().size() );
        assertEquals( 0, tcpServer.getActiveClients().size() );

        log.info( "start server" );
        assertEquals( 0, tcpServer.getClientHandles().size() , 0);
        tcpServer.start();
        Thread.sleep( 20 * 1000 );

        clients.forEach( client -> {
            assertEquals( 1, client.getTcpRemoteServers().size() );
            assertTrue( client.getTcpRemoteServers().get( 0 ).connected.get() );
            assertTrue( client.isConnected() );
        } );

        assertEquals( 10, tcpServer.getClientHandles().size() , 0);
        assertEquals( 10, tcpServer.getActiveClients().size() ,0);

        log.info( "stop" );
        tcpServer.stop();
        Thread.sleep( 10 * 1000 );

        clients.forEach( client -> {
            assertEquals( 1, client.getTcpRemoteServers().size() );
            assertFalse( client.getTcpRemoteServers().get( 0 ).connected.get() );
            assertFalse( client.isConnected() );
        } );

        clients.forEach( ServiceBaseExecutor::stop );

        assertEquals( 0, tcpServer.getClientHandles().size() );
        assertEquals( 0, tcpServer.getActiveClients().size() );

        stopServer();

    }

    @Test
    void client_stop_client_try_reconnect() throws InterruptedException, NoSuchAlgorithmException {

        startServer();

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
        Thread.sleep( 5 * 1000 );

        assertEquals( 0, client1.getTcpRemoteServers().size() );
        assertEquals( 0, client1.getActiveClients().size() );
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
        assertEquals( 0, client1.getActiveClients().size() );
        Thread.sleep( 5 * 1000 );

        assertEquals( 0, tcpServer.getClientHandles().size() );
        assertEquals( 0, tcpServer.getActiveClients().size() );

        stopServer();


    }

    @Test
    void one_client_big_raws_messages() throws InterruptedException, NoSuchAlgorithmException {

        startServer();

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

        client1.stop();
        stopServer();

    }

    @Test
    void one_client_big_messages() throws InterruptedException, NoSuchAlgorithmException {

        startServer();

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

        stopServer();

    }

    @Test
    void many_clients_send_messages() throws InterruptedException, NoSuchAlgorithmException {

        startServer();

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

        clients.forEach( ServiceBaseExecutor::stop );

        clients.forEach( client -> {
            log.info( "{} -> out: {}, in: {}", client.myId, client.outBytes.get(), client.inBytes.get() );
            assertTrue( client.inBytes.get() > 0 && client.outBytes.get() > 0 );
            assertEquals( client.inBytes.get(), client.outBytes.get() );
        } );

        stopServer();

    }

    public static class RandomClient extends TcpClient {

        public RandomClient(String serverHost, int serverPort) {
            super( null, serverPort, serverHost);
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
                Thread.sleep( 1000 );
            } catch (InterruptedException ignored) { }
            log.info( "stop client: {}", myId());
            this.stop();

        }
    }

    @Test
    void random_many_clients() throws InterruptedException, NoSuchAlgorithmException {

        startServer();

        log.info( "random_clients" );
        List<RandomClient> tcpClients = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            RandomClient c = new RandomClient( "localhost", 4000 );
            tcpClients.add( c );
            log.info( "initiate client: {}, {}", c.myId(), i );
        }

        Executor start_task = Executors.newFixedThreadPool( 5 );
        ThreadPoolExecutor execute_task = ( ThreadPoolExecutor ) Executors.newFixedThreadPool( 110 );

        tcpClients.forEach( c -> start_task.execute( () -> {
            final RandomClient client = c;
            client.setReconnectTimeSeconds( 2 );
            client.startWaitConnect( Duration.ofSeconds( 10 ) );
            if (client.isReady()) {
                execute_task.execute( client::runTest );
                log.debug( "{} -> client started", client.myId() );
            } else {
                log.warn( "{} -> client NOT started", client.myId() );
            }
        } ) );

        while (execute_task.getActiveCount() == 0) {
            log.info( "Treads not running: {}", execute_task.getActiveCount() );
            Thread.sleep( 1000 * 5 );
        }
        log.info( "Started threads: {}", execute_task.getActiveCount() );
        while (execute_task.getActiveCount() > 0) {
            log.info( "Treads running: {}", execute_task.getActiveCount() );
            Thread.sleep( 1000 * 5 );
        }

        Thread.sleep( 1000 * 10 );
        tcpClients.forEach( c -> {
            if(c.outBytes.get() - c.inBytes.get()!=0)
                log.info( "{} -> out: {}, in: {}, diff: {}",
                            c.myId,
                            c.outBytes.get(),
                            c.inBytes.get(),
                            c.outBytes.get() - c.inBytes.get() );
            }
        );

        Thread.sleep( 15000 );

        assertEquals( 0, tcpServer.getActiveClients().size() );
        assertEquals( 0, tcpServer.getClientHandles().size() );

        tcpClients.forEach( c -> assertEquals( c.outBytes.get(), c.inBytes.get() ) );
        stopServer();

    }
}
