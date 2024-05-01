package m2.proxy.tcp;

import com.google.protobuf.ByteString;
import m2.proxy.common.TcpException;
import m2.proxy.proto.MessageOuterClass.MessageType;
import m2.proxy.proto.MessageOuterClass.Message;
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
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpCommunicateTest {
    private static final Logger log = LoggerFactory.getLogger( HttpCommunicateTest.class );

    final TcpServer server;

    public HttpCommunicateTest() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance( "RSA" );
        generator.initialize( 2048 );
        KeyPair rsaKey = generator.generateKeyPair();
        log.info( "init server" );
        server = new TcpServer( 4000, null, rsaKey ) {
            final Logger logger = log;
            @Override
            public ConnectionHandler setConnectionHandler() {
                return new ConnectionHandler() {
                    @Override protected void onMessageIn(Message m) {
                        log.info( "{} -> in {}", getMyId(),m.getType() );
                    }
                    @Override protected void onMessageOut(Message m) {
                        log.info( "{} -> out {}", getMyId(),m.getType() );
                    }
                    @Override protected void onConnect(String ClientId, String remoteAddress) {
                        logger.info( "Connect handler: {}, {}", getMyId(), remoteAddress );
                    }
                    @Override protected void onDisonnect(String ClientId, String remoteAddress) { }
                    @Override public void onRequest(long sessionId, long requestId, RequestType type, String address, ByteString request) {
                        logger.info( "Request: {}, {}", sessionId, requestId );
                        getTcpServe().getTaskPool().execute( () -> {
                            try {
                                Thread.sleep( TcpBase.rnd.nextInt( 500 ) + 100 );
                            } catch (InterruptedException ignored) {
                            }
                            reply( sessionId, requestId, type, request );
                        } );
                    }
                };
            }
        };

    }

    public static class Client extends TcpClient {
        private static final Logger log = LoggerFactory.getLogger( BasicCommunicateTest.TcpClient.class );

        public Client(String clientId, int ServerPort, String localport) {
            super( clientId, "127.0.0.1", ServerPort, localport );
        }

        @Override protected boolean onCheckAccess(String accessPath, String remoteAddress, String accessToken, String agent) {
            return true;
        }
        @Override protected Optional<String> onSetAccess(String userId, String passWord, String clientAddress, String accessToken, String agent) {
            return Optional.of( getMyId()+"Key");
        }

        @Override
        public ConnectionHandler setConnectionHandler() {

            log.info( "set client handler" );
            AtomicLong sessionId= new AtomicLong();
            AtomicLong requestId= new AtomicLong();

            return new ConnectionHandler() {
                @Override protected void onMessageIn(Message m) {
                    log.info( "{} -> in {}", getTcpServe().getMyId(), m.getType() );
                    if(m.getType()== MessageType.REQUEST) {
                        log.info( "{} -> request {}", getTcpServe().getMyId(), m.getRequest().getType());
                        sessionId.set(m.getRequest().getSessionId());
                        requestId.set(m.getRequest().getRequestId());
                    }
                }
                @Override protected void onMessageOut(Message m) {
                    log.info( "{} -> out {}", getTcpServe().getMyId(), m.getType() );
                    if(m.getType()== MessageType.REPLY) {
                        assertEquals(sessionId.get(),m.getReply().getSessionId());
                        assertEquals(requestId.get(),m.getReply().getRequestId());
                    }
                }
                @Override protected void onConnect(String ClientId, String remoteAddress) {
                    log.info( "CONNECT: {}, addr: {}", myId, remoteAddress );
                }
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



    @BeforeEach
    void init() { server.start(); }

    @AfterEach
    void end() { server.stop(); }

    @Test
    void test_start() throws InterruptedException {
        Client client1 = new Client( "leif", 4000, "" );
        client1.start();
        client1.waitConnect( Duration.ofSeconds( 5 )  );
        Thread.sleep( 1000 * 30 );
        client1.stop();
    }

    @Test
    void check_request_from_client() throws InterruptedException {

        Client client1 = new Client( "leif", 4000, "" );
        client1.start();
        client1.waitConnect(Duration.ofSeconds( 10 ));
        SessionHandler session = new SessionHandler() {
            @Override
            public void onReceive(long requestId, ByteString reply) {
                log.info( "{} -> {} -> reply: {}", getSessionId(), requestId, reply.toStringUtf8() );
            }
        };

        long start = System.currentTimeMillis();
        while(System.currentTimeMillis()-start<30000) {
            client1.getTcpServers().forEach( (k, s) -> {

                try {
                    s.getHandler().openSession( session, 10000 );
                    //session.sendAsyncRequest( "", ByteString.copyFromUtf8( "hello" ), RequestType.PLAIN );
                    ByteString message = ByteString.copyFromUtf8( "hello sync" );
                    ByteString reply = session.sendRequest( "", message, RequestType.PLAIN, 1000 );
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
        client1.start();
        client1.waitConnect(Duration.ofSeconds( 10 ));
        SessionHandler session = new SessionHandler() {
            @Override
            public void onReceive(long requestId, ByteString reply) {
                log.info( "{} -> {} -> reply: {}", getSessionId(), requestId, reply.toStringUtf8() );
            }
        };

        long start = System.currentTimeMillis();
        while(System.currentTimeMillis()-start<30000) {
            server.getActiveClients().forEach( (k, s) -> {

                try {
                    s.openSession( session, 10000 );
                    //session.sendAsyncRequest( "", ByteString.copyFromUtf8( "hello" ), RequestType.PLAIN );
                    ByteString message = ByteString.copyFromUtf8( "hello sync" );
                    ByteString reply = session.sendRequest( "", message, RequestType.PLAIN, 1000 );
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
    void check_request_logon_from_server() throws InterruptedException {

        Client client1 = new Client( "leif", 4000, "" );
        client1.start();
        client1.waitConnect(Duration.ofSeconds( 10 ));
        SessionHandler session = new SessionHandler() {
            @Override
            public void onReceive(long requestId, ByteString reply) {
                log.info( "{} -> {} -> reply: {}", getSessionId(), requestId, reply.toStringUtf8() );
            }
        };

        long start = System.currentTimeMillis();
        while(System.currentTimeMillis()-start<30000) {
            server.getActiveClients().forEach( (k, s) -> {

                try {
                    s.openSession( session, 10000 );
                    ByteString message = ByteString.copyFromUtf8( "hello sync" );
                    ByteString reply = session.sendRequest( "", message, RequestType.PLAIN, 1000 );
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
    void test_logon() throws InterruptedException {
        Client client1 = new Client( "leif", 4000, "" );
        client1.start();
        client1.waitConnect(Duration.ofSeconds( 120 ));
        assertTrue(server.getActiveClients().size()>0);
        server.getActiveClients().values().forEach( c -> {
            try {
                Optional<String> accessKey = server.logon(c.getRemoteClientId().get(),"","","","","");
                assertTrue(accessKey.isPresent());
            } catch (TcpException e) {
                throw new RuntimeException( e );
            }
        });
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
