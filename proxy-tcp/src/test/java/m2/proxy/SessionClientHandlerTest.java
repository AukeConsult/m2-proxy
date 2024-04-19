package m2.proxy;

import com.google.protobuf.ByteString;
import io.netty.channel.ChannelHandlerContext;
import m2.proxy.tcp.TcpBaseServerBase;
import m2.proxy.tcp.TcpBase;
import m2.proxy.tcp.handlers.ClientHandlerBase;
import m2.proxy.tcp.handlers.SessionHandler;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.m2.MessageOuterClass.*;

import static org.junit.jupiter.api.Assertions.*;

public class SessionClientHandlerTest {
    private static final Logger log = LoggerFactory.getLogger(SessionClientHandlerTest.class);

    @Test
    void check_request() throws InterruptedException {

        SessionHandler session = new SessionHandler() {
            @Override
            public void onReceive(long requestId, ByteString reply) {
                assertEquals("hello",reply.toStringUtf8());
                log.info("{} -> {} -> reply async: {}",getSessionId(),requestId,reply.toStringUtf8());
            }
            @Override
            public void onReceive(long requestId, HttpReply reply) {}
        };

        TcpBaseServerBase server = new TcpBaseServerBase(0, null, null) {
            @Override
            public ClientHandlerBase setClientHandler(String id, ChannelHandlerContext ctx) {
                return new ClientHandlerBase(this) {
                    @Override
                    public void onWrite(Message m) {
                        assertTrue(m.getType()==MessageType.REQUEST || m.getType()==MessageType.REPLY);
                        prosessMessage(m);
                    }

                    @Override
                    public void onInit() {}
                    @Override
                    public void onProsessMessage(Message m) {}
                    @Override
                    public void onRequest(long sessionId, long requestId, RequestType type, ByteString request) {
                        assertEquals(session.getSessionId(),sessionId);
                        getServer().getTaskPool().execute(() -> {
                            try {
                                Thread.sleep(TcpBase.rnd.nextInt(500)+100);
                            } catch (InterruptedException ignored) {
                            }
                            reply(sessionId,requestId,type,request);
                        });
                    }
                };
            }
        };

        ClientHandlerBase handler = server.setClientHandler("leif",null);
        assertNotNull(handler);
        handler.openSession(session, 10000);
        session.sendAsyncRequest(ByteString.copyFromUtf8("hello"),RequestType.PLAIN);
        try {
            ByteString message = ByteString.copyFromUtf8("hello sync");
            ByteString reply = session.sendRequest(message,RequestType.PLAIN,1000);
            assertEquals(message.toStringUtf8(),reply.toStringUtf8());
        } catch (Exception e) {
            log.error("send fail {}",e.getMessage());
        }
        Thread.sleep(10000);

    }

    @Test
    void check_request_async() throws InterruptedException {

        SessionHandler session = new SessionHandler() {
            @Override
            public void onReceive(long requestId, ByteString reply) {
                log.info("{} -> {} -> reply: {}",getSessionId(),requestId,reply.toStringUtf8());
            }
            @Override
            public void onReceive(long requestId, HttpReply reply) {}
        };

        TcpBaseServerBase server = new TcpBaseServerBase(0, null, null) {
            @Override
            public ClientHandlerBase setClientHandler(String id, ChannelHandlerContext ctx) {
                return new ClientHandlerBase(this) {
                    @Override
                    public void onWrite(Message m) {
                        assertTrue(m.getType()==MessageType.REQUEST || m.getType()==MessageType.REPLY);
                        prosessMessage(m);
                    }

                    @Override
                    public void onInit() {}
                    @Override
                    public void onProsessMessage(Message m) {}
                    @Override
                    public void onRequest(long sessionId, long requestId, RequestType type, ByteString request) {
                        assertEquals(session.getSessionId(),sessionId);
                        getServer().getTaskPool().execute(() -> {
                            try {
                                Thread.sleep(TcpBase.rnd.nextInt(500)+100);
                            } catch (InterruptedException ignored) {
                            }
                            reply(sessionId,requestId,type,request);
                        });
                    }
                };
            }
        };

        ClientHandlerBase handler = server.setClientHandler("leif",null);
        assertNotNull(handler);
        handler.openSession(session, 10000);
        server.getTaskPool().execute(() -> {
            int cnt=0;
            while(cnt<10) {
                ByteString message = ByteString.copyFromUtf8("ASYNC hello " + cnt + " " + session.getSessionId());
                session.sendAsyncRequest(message,RequestType.PLAIN);
                try {
                    Thread.sleep(TcpBase.rnd.nextInt(2000));
                } catch (InterruptedException ignored) {
                }
                cnt++;
            }
        });

        server.getTaskPool().execute(() -> {
            int cnt=0;
            while(cnt<10) {
                ByteString message = ByteString.copyFromUtf8("sync hello " + cnt + " " + session.getSessionId());
                try {
                    ByteString ret = session.sendRequest(message,RequestType.PLAIN,5000);
                    assertEquals(message.toStringUtf8(),ret.toStringUtf8());
                    log.info("{} -> reply: {}", session.getSessionId(), ret.toStringUtf8());
                } catch (Exception e) {
                    fail(message.toStringUtf8() + e.getMessage());
                }
                cnt++;
            }
        });

        server.getTaskPool().execute(new Runnable() {
            @Override
            public void run() {
                int cnt=0;
                while(cnt<10) {
                    ByteString message = ByteString.copyFromUtf8("timeout hello " + cnt + " " + session.getSessionId());
                    try {
                        ByteString ret = session.sendRequest(message,RequestType.PLAIN,1);
                        fail();
                    } catch (Exception e) {
                        // log.info("Message: {}, fail {}",message,e.getMessage());
                    }
                    cnt++;
                }
            }
        });
        Thread.sleep(30000);

    }

}
