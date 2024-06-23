package m2.proxy.tcp;

import com.google.protobuf.ByteString;
import m2.proxy.common.TcpException;
import m2.proxy.proto.MessageOuterClass;
import m2.proxy.proto.MessageOuterClass.Message;
import m2.proxy.proto.MessageOuterClass.MessageType;
import m2.proxy.proto.MessageOuterClass.RequestType;
import m2.proxy.tcp.client.TcpClient;
import m2.proxy.tcp.handlers.ConnectionHandler;
import m2.proxy.tcp.handlers.SessionHandler;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RequestTest {
    private static final Logger log = LoggerFactory.getLogger( RequestTest.class );

    final TcpServer server;

    public RequestTest() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance( "RSA" );
        generator.initialize( 2048 );
        KeyPair rsaKey = generator.generateKeyPair();
        log.info( "init server" );
        server = new TcpServer( 4000, null, rsaKey ) {
            final Logger logger = log;

            @Override
            public ConnectionHandler setConnectionHandler() {
                return new ConnectionHandler() {
                    @Override protected void handlerOnMessageIn(Message m) {
                        //log.info( "{} -> in {}", myId(), m.getType() );
                    }
                    @Override protected void handlerOnMessageOut(Message m) {
                        //log.info( "{} -> out {}", myId(), m.getType() );
                    }
                    @Override protected void handlerOnConnect(String ClientId, String remoteAddress) {
                        //logger.info( "Connect handler: {}, {}", myId(), remoteAddress );
                    }
                    @Override protected void handlerOnDisonnect(String ClientId, String remoteAddress) { }
                    @Override public void notifyOnRequest(long sessionId, long requestId, RequestType type, String address, ByteString request) {
                        logger.info( "Request: {}, {}", sessionId, requestId );
                        reply( sessionId, requestId, type, request );
                    }
                };
            }
        };
    }

    public static class Client extends TcpClient {
        private static final Logger log = LoggerFactory.getLogger( BasicsTest.TcpClient.class );

        public Client(String clientId, int ServerPort, String localport) {
            super( clientId, "127.0.0.1", ServerPort, localport );
        }

        @Override protected boolean onCheckAccess(String accessPath, String remoteAddress, String accessToken, String agent) {
            return true;
        }
        @Override protected Optional<String> onSetAccess(String userId, String passWord, String clientAddress, String accessToken, String agent) {
            return Optional.of( myId() + "Key" );
        }

        @Override
        public ConnectionHandler setConnectionHandler() {

            log.trace( "set client handler" );
            AtomicLong sessionId = new AtomicLong();
            AtomicLong requestId = new AtomicLong();

            return new ConnectionHandler() {
                @Override protected void handlerOnMessageIn(Message m) {
                    log.info( "{} -> in {}", getTcpService().myId(), m.getType() );
                    if (m.getType() == MessageType.REQUEST) {
                        log.info( "{} -> request {}", getTcpService().myId(), m.getRequest().getType() );
                        sessionId.set( m.getRequest().getSessionId() );
                        requestId.set( m.getRequest().getRequestId() );
                    }
                }
                @Override protected void handlerOnMessageOut(Message m) {
                    log.info( "{} -> out {}", getTcpService().myId(), m.getType() );
                    if (m.getType() == MessageType.REPLY) {
                        assertEquals( sessionId.get(), m.getReply().getSessionId() );
                        assertEquals( requestId.get(), m.getReply().getRequestId() );
                    }
                }
                @Override protected void handlerOnConnect(String ClientId, String remoteAddress) {
                    log.info( "CONNECT: {}, addr: {}", myId, remoteAddress );
                }
                @Override protected void handlerOnDisonnect(String ClientId, String remoteAddress) { }
                @Override public void notifyOnRequest(long sessionId, long requestId, RequestType type, String destination, ByteString requestMessage) {
                    try {
                        Thread.sleep( new Random().nextInt( 200 ) );
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

    KeyPair rsaKeyClient;

    @BeforeEach
    void init() throws NoSuchAlgorithmException {
        if(rsaKeyClient==null) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance( "RSA" );
            generator.initialize( 2048 );
            rsaKeyClient = generator.generateKeyPair();
        }
        server.start();
    }

    @AfterEach
    void end() { server.stop(); }

    @Test
    void test_start() throws InterruptedException {
        Client client1 = new Client( "leif", 4000, "" );
        client1.start();
        client1.waitConnect( Duration.ofSeconds( 5 ) );
        Thread.sleep( 1000 * 30 );
        client1.stop();
    }

    @Test
    void check_request_from_client() throws InterruptedException {

        Client client1 = new Client( "leif", 4000, "" );
        client1.start();
        client1.waitConnect( Duration.ofSeconds( 10 ) );

        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 30000) {
            client1.getTcpRemoteServers().forEach( s -> {

                try {
                    SessionHandler session = s.getHandler().openSession( 10000 );
                    //session.sendAsyncRequest( "", ByteString.copyFromUtf8( "hello" ), RequestType.PLAIN );
                    ByteString message = ByteString.copyFromUtf8( "hello sync" );
                    ByteString reply = session.sendRequest( "", message, RequestType.PLAIN );
                    assertEquals( message.toStringUtf8(), reply.toStringUtf8() );
                } catch (TcpException | Exception e) {
                    log.error( "Send fail: {}", e.getMessage() );
                }
            } );
            Thread.sleep( 1000 );
        }
        client1.stop();
    }

    @Test
    void check_request_from_server() throws InterruptedException {

        Client client1 = new Client( "leif", 4000, "" );
        client1.startWaitConnect( Duration.ofSeconds( 2 ) );

        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 30000) {
            server.getActiveClients().forEach( (k, s) -> {
                try {
                    SessionHandler session = s.openSession( 10000 );
                    ByteString message = ByteString.copyFromUtf8( "hello sync" );
                    ByteString reply = session.sendRequest( "", message, RequestType.PLAIN );
                    assertEquals( message.toStringUtf8(), reply.toStringUtf8() );
                } catch (TcpException | Exception e) {
                    log.error( "Send fail: {}", e.getMessage() );
                }
            } );
            Thread.sleep( 1000 );
        }
        client1.stop();
    }

    @Test
    void check_request_from_server_many() throws InterruptedException {

        List<BasicsTest.TcpClient> tcpClients = new ArrayList<>( Arrays.asList(
                new BasicsTest.TcpClient( "leif1", 4000, "" ),
                new BasicsTest.TcpClient( "leif2", 4000, "" ),
                new BasicsTest.TcpClient( "leif3", 4000, "" ),
                new BasicsTest.TcpClient( "leif4", 4000, "" ),
                new BasicsTest.TcpClient( "leif5", 4000, "" ),
                new BasicsTest.TcpClient( "leif6", 4000, "" ),
                new BasicsTest.TcpClient( "leif7", 4000, "" ),
                new BasicsTest.TcpClient( "leif8", 4000, "" ),
                new BasicsTest.TcpClient( "leif9", 4000, "" ),
                new BasicsTest.TcpClient( "leif10", 4000, "" ),
                new BasicsTest.TcpClient( "leif11", 4000, "" ),
                new BasicsTest.TcpClient( "leif12", 4000, "" ),
                new BasicsTest.TcpClient( "leif13", 4000, "" ),
                new BasicsTest.TcpClient( "leif14", 4000, "" ),
                new BasicsTest.TcpClient( "leif15", 4000, "" ),
                new BasicsTest.TcpClient( "leif16", 4000, "" ),
                new BasicsTest.TcpClient( "leif17", 4000, "" ),
                new BasicsTest.TcpClient( "leif18", 4000, "" ),
                new BasicsTest.TcpClient( "leif19", 4000, "" ),
                new BasicsTest.TcpClient( "leif20", 4000, "" )
        ) );

        log.info( "Start clients: {}", tcpClients.size() );

        Executor start_task = Executors.newFixedThreadPool( 5 );
        ThreadPoolExecutor execute_task = ( ThreadPoolExecutor ) Executors.newFixedThreadPool( 25 );

        tcpClients.forEach( c -> start_task.execute( () -> {

            final BasicsTest.TcpClient client = c;
            log.debug( "{} -> client prepared", client.myId() );

            client.setReconnectTimeSeconds( 2 );
            client.startWaitConnect( Duration.ofSeconds( 10 ) );
            if (client.isReady()) {
                execute_task.execute( () -> {
                    long start = System.currentTimeMillis();
                    while (System.currentTimeMillis() - start < 30000) {
                        try {
                            ConnectionHandler handler = server.getActiveClients().get( client.myId() );
                            SessionHandler session = handler.openSession( 20000 );
                            //session.sendAsyncRequest( "", ByteString.copyFromUtf8( "hello" ), RequestType.PLAIN );
                            ByteString message = ByteString.copyFromUtf8( "hello sync asdasdasd asd asd asd asdasdasd  " +
                                    "asd asd asd asd asd asd asd " + client.myId );
                            ByteString reply = session.sendRequest( "", message, RequestType.PLAIN );
                            assertEquals( message.toStringUtf8(), reply.toStringUtf8() );
                        } catch (TcpException | Exception e) {
                            log.error( "Send fail: {}", e.getMessage() );
                        }
                    }
                } );
                log.debug( "{} -> client started", client.myId() );
            } else {
                log.warn( "{} -> client NOT started", client.myId() );
            }
        } ) );
        Thread.sleep( 1000 );

        log.info( "All started threads: {}", execute_task.getActiveCount() );

        while (execute_task.getActiveCount() > 0) {
            log.info( "Treads still running: {}", execute_task.getActiveCount() );
            Thread.sleep( 1000 * 5 );
        }

        log.info( "Finish all: {}", execute_task.getActiveCount() );
    }

    @Test
    void check_request_logon_from_server() throws InterruptedException {

        Client client1 = new Client( "leif", 4000, "" );
        client1.startWaitConnect(Duration.ofSeconds( 10 ) );

        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 1000) {
            server.getActiveClients().forEach( (k, s) -> {
                try {
                    SessionHandler session = s.openSession(10000 );
                    Optional<MessageOuterClass.Logon> access = session.logon( "leif","","","","" );
                    assertTrue( access.isPresent() );
                    assertFalse(access.get().getAccessPath().isEmpty());
                } catch (Exception e) {
                    fail( "Send fail: " + e.getMessage() );
                }
            } );
            log.info( "loop" );
        }
        log.info( "end loop" );

        Thread.sleep( 2000 );
        client1.stop();
    }


//    @Test
//    void check_request_async() throws InterruptedException {
//
//        SessionHandler session = new SessionHandler() {
//            @Override
//            public void onReceive(long requestId, ByteString reply) {
//                log.info("{} -> {} -> reply: {}",getSessionId(),requestId,reply.toStringUtf8());
//            }
//        };
//
//        ClientHandler handler = server.setClientHandler("leif",null);
//        assertNotNull(handler);
//        handler.openSession(session, 10000);
//        server.getTaskPool().execute(() -> {
//            int cnt=0;
//            while(cnt<10) {
//                ByteString message = ByteString.copyFromUtf8("ASYNC hello " + cnt + " " + session.getSessionId());
//                try {
//                    session.sendAsyncRequest("",message,RequestType.PLAIN);
//                    Thread.sleep(TcpBase.rnd.nextInt(2000));
//                } catch (InterruptedException | TcpException e) {
//                    fail(message.toStringUtf8() + ">" + e.getMessage());
//                }
//                cnt++;
//            }
//        });
//
//        server.getTaskPool().execute(() -> {
//            int cnt=0;
//            while(cnt<10) {
//                ByteString message = ByteString.copyFromUtf8("sync hello " + cnt + " " + session.getSessionId());
//                try {
//                    ByteString ret = session.sendRequest("",message,RequestType.PLAIN,5000);
//                    assertEquals(message.toStringUtf8(),ret.toStringUtf8());
//                    log.info("{} -> reply: {}", session.getSessionId(), ret.toStringUtf8());
//                } catch (TcpException | Exception e) {
//                    fail(message.toStringUtf8() + ">" + e.getMessage());
//                }
//                cnt++;
//            }
//        });
//
//        server.getTaskPool().execute(() -> {
//            int cnt=0;
//            while(cnt<10) {
//                ByteString message = ByteString.copyFromUtf8("timeout hello " + cnt + " " + session.getSessionId());
//                try {
//                    session.sendRequest("",message,RequestType.PLAIN,1);
//                    fail();
//                } catch (TcpException | Exception e) {
//                    log.info("Message: {}, fail {}",e.getMessage());
//                }
//                cnt++;
//            }
//        });
//        Thread.sleep(30000);
//
//    }
}
