package m2.proxy.tcp;

import com.google.protobuf.ByteString;
import m2.proxy.common.TcpException;
import m2.proxy.proto.MessageOuterClass;
import m2.proxy.proto.MessageOuterClass.RequestType;
import m2.proxy.tcp.handlers.ConnectionHandler;
import m2.proxy.tcp.handlers.SessionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SessionConnectionHandlerTest {
    private static final Logger log = LoggerFactory.getLogger( SessionConnectionHandlerTest.class );

    final TcpBaseServerBase server;

    public static class TcpBaseClient extends TcpBaseClientBase {
        private static final Logger log = LoggerFactory.getLogger( ServerIntegrationTest.TcpBaseClient.class );

        public TcpBaseClient(String clientId, int ServerPort, String localport) {
            super( clientId, "127.0.0.1", ServerPort, localport );
        }

        @Override protected boolean onCheckAccess(String accessPath, String remoteAddress, String accessToken, String agent) {
            return true;
        }
        @Override protected Optional<String> onSetAccess(String userId, String remoteAddress, String accessToken, String agent) {
            return Optional.of(getClientId()+"Key");
        }

        @Override
        public ConnectionHandler setConnectionHandler() {

            log.info( "set client handler" );
            return new ConnectionHandler() {
                @Override protected void onMessageIn(MessageOuterClass.Message m) { }
                @Override protected void onMessageOut(MessageOuterClass.Message m) { }
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

    public SessionConnectionHandlerTest() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance( "RSA" );
        generator.initialize( 2048 );
        KeyPair rsaKey = generator.generateKeyPair();
        log.info( "init server" );
        server = new TcpBaseServerBase( 4000, null, rsaKey ) {
            final Logger logger = log;
            @Override
            public ConnectionHandler setConnectionHandler() {
                return new ConnectionHandler() {
                    @Override protected void onMessageIn(MessageOuterClass.Message m) { }
                    @Override protected void onMessageOut(MessageOuterClass.Message m) { }
                    @Override protected void onConnect(String ClientId, String remoteAddress) {
                        logger.info( "connect handler: {}, {}", clientId, remoteAddress );
                    }
                    @Override protected void onDisonnect(String ClientId, String remoteAddress) { }
                    @Override public void onRequest(long sessionId, long requestId, RequestType type, String address, ByteString request) {
                        logger.info( "request: {}, {}", sessionId, requestId );
                        getServer().getTaskPool().execute( () -> {
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

    @BeforeEach
    void init() {
        server.start();
    }

    @AfterEach
    void end() {
        server.stop();
    }

    @Test
    void test_start() throws InterruptedException {
        TcpBaseClient client1 = new TcpBaseClient( "leif", 4000, "" );
        client1.start();
        Thread.sleep( 1000 * 30 );
        client1.stop();
    }

    @Test
    void check_request() throws InterruptedException {

        TcpBaseClient client1 = new TcpBaseClient( "leif", 4000, "" );
        client1.start();
        SessionHandler session = new SessionHandler() {
            @Override
            public void onReceive(long requestId, ByteString reply) {
                log.info( "{} -> {} -> reply: {}", getSessionId(), requestId, reply.toStringUtf8() );
            }
        };

        client1.getClients().forEach( (k, s) -> {

            try {
                s.getHandler().openSession( session, 10000 );
                session.sendAsyncRequest( "", ByteString.copyFromUtf8( "hello" ), RequestType.PLAIN );
                ByteString message = ByteString.copyFromUtf8( "hello sync" );
                ByteString reply = session.sendRequest( "", message, RequestType.PLAIN, 1000 );
                assertEquals( message.toStringUtf8(), reply.toStringUtf8() );
            } catch (TcpException | Exception e) {
                log.error( "Send fail: {}", e.getMessage() );
            }


        } );

        Thread.sleep( 10000 );
        Thread.sleep( 1000 * 30 );
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
