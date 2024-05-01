package m2.proxy;

import com.google.protobuf.ByteString;
import m2.proxy.proto.MessageOuterClass;
import m2.proxy.tcp.TcpServer;
import m2.proxy.tcp.handlers.ConnectionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.Runtime.getRuntime;

public class ProxyTcpServer {
    private static final Logger log = LoggerFactory.getLogger( ProxyTcpServer.class );
    static ProxyTcpServer app;

    TcpServer proxyTcpServer;

    void run(String[] args) {

        int serverPort = 9001;
        String localAddress = null;

        if (args.length > 0) {
            int cnt = 0;
            while (cnt < args.length) {
                if (args[ cnt ].equals( "-p" )) {
                    serverPort = Integer.parseInt( args[ cnt + 1 ] );
                    cnt++;
                } else if (args[ cnt ].equals( "-addr" )) {
                    localAddress = args[ cnt + 1 ];
                    cnt++;
                }
                cnt++;
            }
        }

        proxyTcpServer = new TcpServer( serverPort, localAddress, null ) {
            @Override
            public ConnectionHandler setConnectionHandler() {
                return new ConnectionHandler() {
                    @Override
                    protected void onMessageIn(MessageOuterClass.Message m) { }
                    @Override
                    protected void onMessageOut(MessageOuterClass.Message m) { }
                    @Override
                    protected void onConnect(String ClientId, String remoteAddress) { }
                    @Override
                    protected void onDisonnect(String ClientId, String remoteAddress) { }
                    @Override
                    protected void onRequest(long sessionId, long requestId, MessageOuterClass.RequestType type, String address, ByteString request) { }
                };
            }
        };
        proxyTcpServer.start();

        getRuntime().addShutdownHook( new Thread( () -> proxyTcpServer.stop() ) );
    }
    public static void main(String[] args) {
        app = new ProxyTcpServer();
        app.run( args );
    }

}