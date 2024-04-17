package m2.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.Runtime.getRuntime;

public class NettyClientApp {
    private static final Logger log = LoggerFactory.getLogger(NettyClientApp.class);
    static NettyClientApp app;

    NettyClient nettyClient;

    void run(String[] args) {

        int serverPort=9001;
        String serverAddr=null;
        String localAddress=null;
        String clientId=null;

        if(args.length>0) {
            int cnt = 0;
            while(cnt<args.length){
                if(args[cnt].equals("-host")) {
                    String addr = args[cnt+1];
                    String[] addrPart = addr.split(":");
                    if(addrPart.length<=1) {
                        serverAddr = addrPart[0];
                    }
                    if(addrPart.length<=2) {
                        serverPort = Integer.parseInt(addrPart[1]);
                    }
                    cnt++;
                } else if(args[cnt].equals("-addr")) {
                    localAddress = args[cnt+1];
                    cnt++;
                } else if(args[cnt].equals("-id")) {
                    clientId = args[cnt+1];
                    cnt++;
                }
                cnt++;
            }
        }

        serverAddr = serverAddr==null?Network.localAddress():serverAddr;
        serverAddr = "127.0.0.1";

        nettyClient = new NettyClient(clientId,serverAddr,serverPort,localAddress);
        nettyClient.start();

        getRuntime().addShutdownHook(new Thread(() -> {
            nettyClient.stop();
        }));

    }

    public static void main(String[] args) {
        app= new NettyClientApp();
        app.run(args);
    }

}