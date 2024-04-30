package m2.proxy.tcp;

import m2.proxy.tcp.handlers.ConnectionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@SuppressWarnings( "ALL" )
public abstract class TcpBaseClientBase extends TcpBase {
    private static final Logger log = LoggerFactory.getLogger( TcpBaseClientBase.class );
    private static final long RESTART_WAIT = 2000;

    private final Map<String, TcpClientWorker> clients = new ConcurrentHashMap<>();
    public Map<String, TcpClientWorker> getClients() { return clients; }

    public TcpBaseClientBase(String clientId, String serverAddr, int serverPort, String localAddress) {
        super(
                clientId == null ? UUID.randomUUID().toString().substring( 0, 5 ) : clientId,
                serverAddr, serverPort, localAddress, null
        );
        setLocalPort( 0 );
    }

    @Override
    public final void disConnect(ConnectionHandler handler) {

        getExecutor().execute( () -> {
            try {

                handler.getConnectionWorker().stopWorker();
                Thread.sleep( RESTART_WAIT );
                String key = this.serverAddress + this.serverPort;
                getClients().remove( key );

                log.info( "{} -> Client reconnect: {}:{}", getClientId(), this.serverAddress, this.serverPort );
                TcpClientWorker tcpClientWorker = new TcpClientWorker( this, this.serverAddress, this.serverPort );
                tcpClientWorker.running.set( true );
                getExecutor().execute( tcpClientWorker );
                getClients().put( key, tcpClientWorker );
            } catch (InterruptedException ignored) {
            }
        } );
    }

    @Override
    public final void connect(ConnectionHandler handler) {
        handler.startPing( 10 );
        handler.getCtx().executor().scheduleAtFixedRate( () ->
                        handler.sendMessage( "Hello server: " + System.currentTimeMillis() )
                , 2, 10, TimeUnit.SECONDS );
    }

    private void startServers() {
        String key = this.serverAddress + this.serverPort;
        if (getClients().containsKey( key )) {
            if (!getClients().get( key ).running.getAndSet( true )) {
                getClients().get( key ).stopWorker();
                getClients().remove( key );
            }
        }
        TcpClientWorker tcpClientWorker = new TcpClientWorker( this, this.serverAddress, this.serverPort );
        tcpClientWorker.running.set( true );
        getExecutor().execute( tcpClientWorker );
        getClients().put( key, tcpClientWorker );
    }

    @Override
    public void onStart() {
        log.info( "Netty client start on {}:{}, connect to host -> {}:{}",
                getLocalAddress(), getLocalPort(), serverAddress, serverPort );
        startServers();
    }

    @Override
    public void onStop() {
        getClients().values().forEach( s -> {
            s.getHandler().sendDisconnect();
            s.getHandler().printWork();
            getActiveClients().remove( s.getHandler().getClientId() );
            if (s.running.get()) {
                s.stopWorker();
            }
        } );
        getClients().clear();
    }

    @Override
    protected void execute() {
        while (isRunning()) {
            waitfor( 10000 );
            getClients().values().forEach( s -> s.getHandler().printWork());
        }
    }
}
