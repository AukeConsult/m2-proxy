package m2.proxy.tcp.client;

import m2.proxy.tcp.TcpBase;
import m2.proxy.tcp.handlers.ConnectionHandler;
import m2.proxy.tcp.handlers.ConnectionWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings( "ALL" )
public abstract class TcpClient extends TcpBase {
    private static final Logger log = LoggerFactory.getLogger( TcpClient.class );

    // parameteres
    private final AtomicInteger reconnectTime = new AtomicInteger();
    public int getReconnectTime() { return reconnectTime.get(); }
    public void setReconnectTimeSeconds(int reconnectTime) { this.reconnectTime.set( reconnectTime * 1000 ); }

    private final Map<String, TcpClientWorker> tcpRemoteServers = new ConcurrentHashMap<>();
    public List<TcpClientWorker> getTcpRemoteServers() { return new ArrayList<>(tcpRemoteServers.values()); }

    public boolean isConnected() {
        AtomicBoolean ready = new AtomicBoolean( false );
        tcpRemoteServers.values().forEach( w -> {
            if (!ready.getAndSet( w.isReady() )) ;
        } );
        return ready.get();
    }

    public boolean waitConnect(Duration dureation) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < dureation.toMillis()) {
            if (isConnected()) {
                return true;
            }
            try { Thread.sleep( 500 ); } catch (InterruptedException ignored) { }
        }
        return false;
    }
    public boolean startWaitConnect(Duration duration) {
        start();
        return waitConnect( duration );
    }
    public boolean isReady() { return isRunning() && isConnected(); }

    private void startServerWorkers() {

        final String workerId = this.myAddress + this.myPort;
        getTaskPool().execute( () -> {
            if (tcpRemoteServers.containsKey( workerId )) {
                if (tcpRemoteServers.get( workerId ).running.getAndSet( false )) {
                    tcpRemoteServers.get( workerId ).disconnect( true );
                    tcpRemoteServers.remove( workerId );
                }
            }
            TcpClientWorker tcpClientWorker = new TcpClientWorker( workerId, this, this.myAddress, this.myPort );
            getTaskPool().execute( tcpClientWorker );
            tcpRemoteServers.put( workerId, tcpClientWorker );
        } );
    }

    public TcpClient(String clientId, String serverAddr, int serverPort, String localAddress) {
        super(
                clientId == null ? UUID.randomUUID().toString().substring( 0, 10 ) : clientId,
                serverAddr, serverPort, localAddress, null
        );
        final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>(100);
        taskPool = new ThreadPoolExecutor( 10, 50, 30, TimeUnit.SECONDS, tasks );
        setLocalPort( 0 );
    }

    @Override public final void connect(ConnectionHandler handler) {
        handler.startPing( 10 );
    }

    @Override public final void doDisconnect(ConnectionHandler handler) {
        // stop everything and notify remote server
        handler.getConnectionWorker().disconnect( true );
    }

    @Override public final void onDisconnected(ConnectionHandler handler) {
        // make thread to reconnect
        getTaskPool().execute( () -> {

            ConnectionWorker tcpClientWorker = handler.getConnectionWorker();
            // just stop everything without notify remote server
            tcpClientWorker.disconnect( false );

            log.info( "{} -> Start server reconnect in: {} ms", getMyId(), getReconnectTime() );
            try {
                Thread.sleep( getReconnectTime() );
            } catch (InterruptedException ignored) {
            }
            log.info( "{} -> Client reconnect: {}:{}", getMyId(), this.myAddress, this.myPort );
            getTaskPool().execute( tcpClientWorker );

        } );
    }

    @Override public void onStart() {
        log.info( "Netty client start on {}:{}, connect to host -> {}:{}",
                getLocalAddress(), getLocalPort(), myAddress, myPort );
        startServerWorkers();
    }

    @Override public void onStop() {
        tcpRemoteServers
                .forEach( (k,s) -> {
                    s.getHandler().printWork();
                    doDisconnect( s.getHandler() );
                    getActiveClients().remove( s.getHandler().getRemoteClientId() );
                } );

        tcpRemoteServers.clear();
    }

    @Override protected void execute() {
        while (isRunning()) {
            waitfor( 10000 );
            getTcpRemoteServers().forEach( s -> s.getHandler().printWork() );
        }
    }
}
