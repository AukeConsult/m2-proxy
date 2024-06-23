package m2.proxy.tcp.server;

import m2.proxy.server.AccessSession;
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

    private final AccessSession accessSession = new AccessSession( this );
    public AccessSession getAccessSession() { return accessSession; }

    private TcpServerWorker tcpServerWorker;

    public TcpServer(int serverPort, String localAddress, KeyPair rsaKey) {
        super( "SERVER", localAddress, serverPort, localAddress, rsaKey );
        setLocalPort( serverPort );
        // set pool parameters
        corePoolSize = 50;
        maximumPoolSize = 200;
        keepAliveTime = 15;
    }

    @Override public final void disconnect(ConnectionHandler handler) {

        if(clientHandles.containsKey( handler.getChannelId() )) {

            // remove from active list
            clientHandles.remove( handler.getChannelId() );
            Thread.yield();
            handler.disconnectRemote();
            log.info( "{} -> disconnect client: {}, addr: {}, handlers: {}",
                    myId(),
                    handler.getRemoteClientId(),
                    handler.getRemotePublicAddress(),
                    clientHandles.size()
            );
        }

    }

    @Override public final void serviceDisconnected(ConnectionHandler handler, String cause) {

        if(!clientHandles.containsKey( handler.getChannelId() )) {
            log.error("{} -> serviceDisconnected, handle dont exits {}",myId(), handler.getChannelId());
        }

        log.info( "{} -> got disconnect from client: {}, addr: {}, cause: {}, open clients: {}",
                myId(),
                handler.getRemoteClientId(),
                handler.getRemotePublicAddress(),
                cause,
                clientHandles.size()
        );

        handler.removeActiveHandler();
        clientHandles.remove( handler.getChannelId() );
        Thread.yield();

    }
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
        tcpServerWorker.disconnect( true );
        while (tcpServerWorker.running.get() && tcpServerWorker.stopping.get()) {
            try {
                Thread.sleep( 10 );
            } catch (InterruptedException ignored) {
            }
        }
    }

    @Override final protected void execute() {
        while (isRunning()) {
            waitfor( 1000 );
        }
    }
}
