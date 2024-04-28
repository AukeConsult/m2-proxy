package m2.proxy;

import com.google.protobuf.ByteString;
import m2.proxy.proto.MessageOuterClass.Message;
import m2.proxy.proto.MessageOuterClass.RequestType;
import m2.proxy.tcp.TcpBaseClientBase;
import m2.proxy.tcp.handlers.ConnectionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

import static java.lang.Runtime.getRuntime;

public class ProxyTcpClient {
    private static final Logger log = LoggerFactory.getLogger( ProxyTcpClient.class );
    static ProxyTcpClient app;

    TcpBaseClientBase proxyTcpClient;

    void run(String[] args) {

        int serverPort = 9001;
        String serverAddr = "127.0.0.1";
        String localAddress = null;
        String clientId = null;

        if (args.length > 0) {
            int cnt = 0;
            while (cnt < args.length) {
                switch (args[ cnt ]) {
                    case "-host" -> {
                        String addr = args[ cnt + 1 ];
                        String[] addrPart = addr.split( ":" );
                        if (addrPart.length <= 1) {
                            serverAddr = addrPart[ 0 ];
                        }
                        if (addrPart.length <= 2) {
                            serverPort = Integer.parseInt( addrPart[ 1 ] );
                        }
                        cnt++;
                    }
                    case "-addr" -> {
                        localAddress = args[ cnt + 1 ];
                        cnt++;
                    }
                    case "-id" -> {
                        clientId = args[ cnt + 1 ];
                        cnt++;
                    }
                }
                cnt++;
            }
        }

        //serverAddr = serverAddr==null?Network.localAddress():serverAddr;


        proxyTcpClient = new TcpBaseClientBase( clientId, serverAddr, serverPort, localAddress ) {
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
                            if (type == RequestType.PLAIN) {
                                reply( sessionId, requestId, type, requestMessage );
                            } else if (type == RequestType.HTTP) {
                                reply( sessionId, requestId, type, requestMessage );
                            } else {
                                reply(
                                        sessionId,
                                        requestId,
                                        RequestType.NONE,
                                        null
                                     );
                            }
                            Thread.sleep( new Random().nextInt( 2000 ) );

                        } catch (InterruptedException e) {
                            throw new RuntimeException( e );
                        }
                    }
                };
            }
        };

        proxyTcpClient.start();

        getRuntime().addShutdownHook( new Thread( () -> proxyTcpClient.stop() ) );

    }

    public static void main(String[] args) {
        app = new ProxyTcpClient();
        app.run( args );
    }

}