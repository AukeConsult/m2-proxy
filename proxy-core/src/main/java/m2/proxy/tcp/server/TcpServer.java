package m2.proxy.tcp.server;

import m2.proxy.server.tcp.SessionsHandler;
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

    private final SessionsHandler sessionsHandler = new SessionsHandler( this );
    public SessionsHandler getAccessSession() { return sessionsHandler; }

    private TcpServerWorker tcpServerWorker;

    public TcpServer(int serverPort, String localAddress, KeyPair rsaKey) {
        super( "SERVER", localAddress, serverPort, localAddress, rsaKey );
        setLocalPort( serverPort );
        // set pool parameters
        corePoolSize = 50;
        maximumPoolSize = 200;
        keepAliveTime = 15;
    }

    @Override public final void disconnectRemote(ConnectionHandler handler) {

        log.debug( "{} -> do disconnect client: {}",myId,handler.getRemoteClientId());
        if(clientHandles.containsKey( handler.getChannelId() )) {
            // remove from active list
            Thread.yield();
            handler.disconnectRemote();
            log.info( "{} -> disconnect client: {}, addr: {}, active handlers: {}",
                    myId(),
                    handler.getRemoteClientId(),
                    handler.getRemotePublicAddress(),
                    clientHandles.size()
            );
            clientHandles.remove( handler.getChannelId() );

        }
    }

    // got a disconnect from client side
    @Override public final void gotClientDisconnect(ConnectionHandler handler, String cause) {

        if(!clientHandles.containsKey( handler.getChannelId() )) {
            log.error("{} -> serviceDisconnected, client handle dont exits {}",myId(), handler.getChannelId());
        }
        clientHandles.remove( handler.getChannelId() );
        log.info( "{} -> got disconnect from client: {}, addr: {}, cause: {}, active handles left: {}",
                myId(),
                handler.getRemoteClientId(),
                handler.getRemotePublicAddress(),
                cause,
                clientHandles.size()
        );
        handler.removeActiveHandler();
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

    @Override protected final void onStart() {
        log.info( "{} -> Starting server on -> {}:{} ", myId(), localAddress(), localPort() );
        tcpServerWorker = new TcpServerWorker( this );
        getTaskPool().execute( tcpServerWorker );
    }

    @Override protected final void onStop() {
        log.info( "{} -> Stopping server on -> {}:{} ", myId(), localAddress(), localPort() );
        tcpServerWorker.disconnectRemote( true );
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
