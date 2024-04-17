package m2.proxy;

import io.micronaut.context.annotation.ConfigurationProperties;
import jakarta.inject.Singleton;
import m2.proxy.executors.ServiceBase;

@Singleton
@ConfigurationProperties("netty-server")
public class TcpServerMain extends ServiceBase {

    private NettyServer server;
    // parameter
    public int port=5000;

    public boolean isRunning() { return server!=null&&server.isRunning(); }

    @Override
    public void start() {
        server = new NettyServer(port,null,null);
        server.start();
    }
    @Override
    public void stop() {
        server.stop();
    }
}
