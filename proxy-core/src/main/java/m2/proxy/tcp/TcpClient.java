package m2.proxy.tcp;

import m2.proxy.tcp.handlers.ConnectionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings( "ALL" )
public abstract class TcpClient extends TcpBase {
    private static final Logger log = LoggerFactory.getLogger( TcpClient.class );
    private static final long RESTART_WAIT = 2000;

    private final Map<String, TcpClientServerWorker> tcpServers = new ConcurrentHashMap<>();
    public Map<String, TcpClientServerWorker> getTcpServers() { return tcpServers; }

    public boolean isConnected() {
        AtomicBoolean ready = new AtomicBoolean( false );
        tcpServers.values().forEach( w -> {
            if (!ready.getAndSet( w.isReady() )) ;
        } );
        return ready.get();
    }
    public boolean waitConnect(Duration dureation) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < dureation.toMillis()) {
            if(isConnected()) {
                return true;
            }
            try { Thread.sleep( 2 ); } catch (InterruptedException ignored) { }
        }
        return false;
    }
    public boolean startWaitConnect(Duration duration) {
        start();
        return waitConnect(duration);
    }
    public boolean isReady() { return isRunning() && isConnected(); }


    public TcpClient(String clientId, String serverAddr, int serverPort, String localAddress) {
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
                handler.getConnectionWorker().running.set( false );
                handler.getConnectionWorker().connected.set( false );

                Thread.sleep( RESTART_WAIT );
                String key = this.myAddress + this.myPort;
                getTcpServers().remove( key );

                log.info( "{} -> Client reconnect: {}:{}", getMyId(), this.myAddress, this.myPort );
                TcpClientServerWorker tcpClientServerWorker = new TcpClientServerWorker( this, this.myAddress, this.myPort );
                tcpClientServerWorker.running.set( true );
                getExecutor().execute( tcpClientServerWorker );
                getTcpServers().put( key, tcpClientServerWorker );
            } catch (InterruptedException ignored) {
            }
        } );
    }

    @Override
    public final void connect(ConnectionHandler handler) {
        handler.startPing( 10 );
//        handler.getCtx().executor().scheduleAtFixedRate( () ->
//                        handler.sendMessage( "Hello server: " + System.currentTimeMillis() )
//                , 2, 10, TimeUnit.SECONDS );
    }

    private void startServerWorkers() {

        final String key = this.myAddress + this.myPort;
        getExecutor().execute( () -> {
            if (getTcpServers().containsKey( key )) {
                if (getTcpServers().get( key ).running.getAndSet( false )) {
                    getTcpServers().get( key ).stopWorker();
                    getTcpServers().remove( key );
                }
            }
            TcpClientServerWorker tcpClientServerWorker = new TcpClientServerWorker( this, this.myAddress, this.myPort );
            tcpClientServerWorker.running.set( true );
            getExecutor().execute( tcpClientServerWorker );
            getTcpServers().put( key, tcpClientServerWorker );
        } );

    }

    @Override
    public void onStart() {
        log.info( "Netty client start on {}:{}, connect to host -> {}:{}",
                getLocalAddress(), getLocalPort(), myAddress, myPort );
        startServerWorkers();
    }

    @Override
    public void onStop() {
        getTcpServers().values().forEach( s -> {
            s.getHandler().sendDisconnect();
            s.getHandler().printWork();
            getActiveClients().remove( s.getHandler().getRemoteClientId() );
            if (s.running.getAndSet( false )) {
                s.stopWorker();
            }
        } );
        getTcpServers().clear();
    }

    @Override
    protected void execute() {
        while (isRunning()) {
            waitfor( 10000 );
            getTcpServers().values().forEach( s -> s.getHandler().printWork() );
        }
    }

}
