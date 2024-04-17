package m2.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static java.lang.Runtime.getRuntime;

public class NettyServerApp {
    private static final Logger log = LoggerFactory.getLogger(NettyServerApp.class);
    static NettyServerApp app;

    NettyServer nettyServer;

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

        nettyServer = new NettyServer(serverPort, localAddress, null);
        nettyServer.start();

        getRuntime().addShutdownHook(new Thread(() -> {
            nettyServer.stop();
        }));
    }
    public static void main(String[] args) {
        app= new NettyServerApp();
        app.run(args);
    }

}