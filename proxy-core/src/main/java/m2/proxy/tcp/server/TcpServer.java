package m2.proxy.tcp.server;

import m2.proxy.tcp.TcpBase;
import m2.proxy.tcp.handlers.ConnectionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyPair;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public abstract class TcpServer extends TcpBase {
    private static final Logger log = LoggerFactory.getLogger( TcpServer.class );

    private final Map<String, ConnectionHandler> clientHandles = new ConcurrentHashMap<>();
    public Map<String, ConnectionHandler> getClientHandles() { return clientHandles; }

    private final TcpSession tcpSession = new TcpSession( this );
    public TcpSession getTcpSession() { return tcpSession; }

    private TcpServerWorker tcpServerWorker;

    public TcpServer(int serverPort, String localAddress, KeyPair rsaKey) {
        super( "SERVER", localAddress, serverPort, localAddress, rsaKey );
        setLocalPort( serverPort );
        // set pool parameters
        corePoolSize = 50;
        maximumPoolSize = 200;
        keepAliveTime = 15;
    }

    @Override public final void doDisconnect(ConnectionHandler handler) {

        // remove from active list
        clientHandles.remove( handler.getChannelId() );
        Thread.yield();
        handler.disconnectRemote();
        log.debug( "{} -> disconnect ch: {}, client: {}, addr: {}",
                myId(),
                handler.getChannelId(),
                handler.getRemoteClientId(),
                handler.getRemotePublicAddress()
        );
    }

    @Override public final void onDisconnected(ConnectionHandler handler) { }

    @Override public final void connect(ConnectionHandler handler) {
        handler.startPing( 2 );
    }

    @Override protected boolean onCheckAccess(String accessPath, String clientAddress, String accessToken, String agent) {
        return true;
    }
    @Override protected Optional<String> onSetAccess(String userId, String passWord, String clientAddress, String accessToken, String agent) {
        return Optional.empty();
    }

    @Override public final void onStart() {
        log.info( "{} -> Starting netty server on -> {}:{} ", myId(), localAddress(), localPort() );
        tcpServerWorker = new TcpServerWorker( this );
        getTaskPool().execute( tcpServerWorker );
    }

    @Override public final void onStop() {
        tcpServerWorker.stop( true );
        while (tcpServerWorker.running.get() && tcpServerWorker.stopping.get()) {
            try {
                Thread.sleep( 10 );
            } catch (InterruptedException e) {
            }
        }
    }

    @Override final protected void execute() {
        while (isRunning()) {
            waitfor( 1000 );
        }
    }
}
