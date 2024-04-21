package m2.proxy;

import com.google.protobuf.ByteString;
import io.netty.channel.ChannelHandlerContext;
import m2.proxy.tcp.TcpBaseServerBase;
import m2.proxy.tcp.handlers.ClientHandler;
import m2.proxy.tcp.handlers.ClientHandlerBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.m2.MessageOuterClass;

import static java.lang.Runtime.getRuntime;

public class ProxyTcpServer {
    private static final Logger log = LoggerFactory.getLogger(ProxyTcpServer.class);
    static ProxyTcpServer app;

    TcpBaseServerBase proxyTcpServer;

    void run(String[] args) {

        int serverPort=9001;
        String localAddress=null;

        if(args.length>0) {
            int cnt = 0;
            while(cnt<args.length){
                if(args[cnt].equals("-p")) {
                    serverPort = Integer.parseInt(args[cnt+1]);
                    cnt++;
                } else if(args[cnt].equals("-addr")) {
                    localAddress = args[cnt+1];
                    cnt++;
                }
                cnt++;
            }
        }

        proxyTcpServer = new TcpBaseServerBase(serverPort, localAddress, null) {
            @Override
            public ClientHandlerBase setClientHandler(String channelId, ChannelHandlerContext ctx) {
                getClients().put(channelId, new ClientHandler(this, channelId, ctx) {
                    @Override
                    protected void onRequest(long sessionId, long requestId, MessageOuterClass.RequestType type, String address, ByteString request) {}
                });
                getClients().get(channelId).onInit();
                return getClients().get(channelId);
            }
        };
        proxyTcpServer.start();

        getRuntime().addShutdownHook(new Thread(() -> proxyTcpServer.stop()));
    }
    public static void main(String[] args) {
        app= new ProxyTcpServer();
        app.run(args);
    }

}