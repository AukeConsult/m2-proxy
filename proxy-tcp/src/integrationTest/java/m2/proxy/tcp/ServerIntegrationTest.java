package m2.proxy.tcp;

import com.google.protobuf.ByteString;
import io.netty.channel.ChannelHandlerContext;
import m2.proxy.executors.*;
import m2.proxy.tcp.handlers.ClientHandler;
import m2.proxy.tcp.handlers.ClientHandlerBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.m2.MessageOuterClass;

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
        private static final Logger log = LoggerFactory.getLogger(TcpBaseClient.class);

        public TcpBaseClient(String clientId, int ServerPort, String localport) {
            super(clientId, "127.0.0.1", ServerPort, localport);
        }

        @Override
        public ClientHandler setClientHandler(String channelId, ChannelHandlerContext ctx) {

            log.info("set client handler");
            return new ClientHandler(this, channelId, ctx) {
                @Override
                public void onRequest(long sessionId, long requestId, MessageOuterClass.RequestType type, String destination, ByteString requestMessage) {
                    try {
                        if(type== MessageOuterClass.RequestType.PLAIN) {
                            reply(sessionId,requestId,type,ByteString.copyFromUtf8("hello back PLAIN"));
                        } else if (type== MessageOuterClass.RequestType.HTTP ) {
                            reply(sessionId,requestId,type,ByteString.copyFromUtf8("hello back HTTP"));
                        } else {
                            reply(sessionId,
                                    requestId,
                                    MessageOuterClass.RequestType.NONE,
                                    null
                            );
                        }
                        Thread.sleep(new Random().nextInt(2000));

                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            };
        }

    }

    public ServerIntegrationTest() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair rsaKey = generator.generateKeyPair();
        server = new TcpBaseServerBase(4000, "", rsaKey) {
            @Override
            public ClientHandlerBase setClientHandler(String id, ChannelHandlerContext ctx) {
                return null;
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
    void one_client() throws InterruptedException {

        TcpBaseClient client1 = new TcpBaseClient("localhost",4000, "");
        client1.start();
        Thread.sleep(1000*30);
        client1.stop();

    }

    @Test
    void one_client_big_message() throws InterruptedException {

        TcpBaseClient client1 = new TcpBaseClient(null,4000, "localhost");
        client1.start();
        Thread.sleep(1000);

        byte[] b = new byte[1000000];
        rnd.nextBytes(b);
        client1.getHandler().sendMessage(new String(b));

        Thread.sleep(1000*30);
        client1.stop();

    }


    @Test
    void many_clients() throws InterruptedException {

        TcpBaseClient client1 = new TcpBaseClient(null,4000, "localhost");
        TcpBaseClient client2 = new TcpBaseClient(null,4000, "localhost");
        TcpBaseClient client3 = new TcpBaseClient(null,4000, "localhost");

        client1.start();
        client2.start();
        client3.start();

        Thread.sleep(1000*15);
        client1.stop();
        client2.stop();
        client3.stop();

    }

    public static class RandomClient extends TcpBaseClient {

        public RandomClient(String host, int port) {
            super(null, port, host);
        }

        @Override
        protected void startServices() {
            super.startServices();

            TcpBaseClient server = this;
            getExecutor().execute(() -> {
                while(this.isRunning()) {
                    int wait = rnd.nextInt(200);
                    if(server.getHandler()!=null) {
                        byte[] bytes = new byte[rnd.nextInt(200000)+10];
                        rnd.nextBytes(bytes);
                        server.getHandler().sendMessage(new String(bytes));
                    }
                    try {
                        Thread.sleep(wait);
                    } catch (InterruptedException ignored) {
                    }
                }
            });
        }
    }

    @Test
    void random_clients() throws InterruptedException {

        List<RandomClient> clients= new ArrayList<>();
        for(int i = 0;i<5;i++) {
            RandomClient c = new RandomClient("localhost", 4000);
            clients.add(c);
            c.start();
        }
        Thread.sleep(1000*30);
        clients.forEach(ServiceBaseExecutor::stop);

    }

}
