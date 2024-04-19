package m2.proxy;

import m2.proxy.tcp.TcpBaseServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static java.lang.Runtime.getRuntime;

public class ProxyTcpServer {
    private static final Logger log = LoggerFactory.getLogger(ProxyTcpServer.class);
    static ProxyTcpServer app;

    TcpBaseServer proxyTcpServer;

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

        proxyTcpServer = new TcpBaseServer(serverPort, localAddress, null);
        proxyTcpServer.start();

        getRuntime().addShutdownHook(new Thread(() -> proxyTcpServer.stop()));
    }
    public static void main(String[] args) {
        app= new ProxyTcpServer();
        app.run(args);
    }

}