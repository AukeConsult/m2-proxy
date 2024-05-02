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

@SuppressWarnings( "ALL" )
public abstract class TcpClient extends TcpBase {
    private static final Logger log = LoggerFactory.getLogger( TcpClient.class );

    // parameteres

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

    private void startWorker(String workerId) {
        TcpClientWorker tcpClientWorker = new TcpClientWorker( workerId, this, this.myAddress, this.myPort );
        getTaskPool().execute( tcpClientWorker );
        tcpRemoteServers.put( workerId, tcpClientWorker );
    }

    private void startServerWorkers() {
        final String workerId = this.myAddress + this.myPort;
        startWorker(workerId);
    }

    public TcpClient(String clientId, String serverAddr, int serverPort, String localAddress) {
        super(
                clientId == null ? UUID.randomUUID().toString().substring( 0, 10 ) : clientId,
                serverAddr, serverPort, localAddress, null
        );
        setLocalPort( 0 );
    }

    @Override public final void connect(ConnectionHandler handler) { }

    @Override public final void doDisconnect(ConnectionHandler handler) {
        // stop everything and notify remote server
        handler.getConnectionWorker().stop( true );
    }

    @Override public final void onDisconnected(ConnectionHandler handler) {
        // make thread to reconnect
        if(handler.getConnectionWorker().running.get()) {
            getTaskPool().execute( () -> {

                TcpClientWorker tcpClientWorker = (TcpClientWorker)handler.getConnectionWorker();
                // just stop everything without notify remote server
                tcpClientWorker.stop( false );
                Thread.yield();

                log.info( "{} -> Start server reconnect in: {} ms", myId(), reconnectTime() );
                try {
                    Thread.sleep( reconnectTime() );
                } catch (InterruptedException ignored) {
                }
                log.info( "{} -> Client reconnect: {}:{}", myId(), this.myAddress, this.myPort );
                startWorker( tcpClientWorker.getWorkerId() );
                Thread.yield();

            } );
        }
    }

    @Override public void onStart() {
        log.debug( "Netty client start on {}:{}, connect to host -> {}:{}",
                localAddress(), localPort(), myAddress, myPort );
        startServerWorkers();
    }

    @Override public void onStop() {

        tcpRemoteServers
                .forEach( (k,s) -> {
                    s.getHandler().printWork();
                    doDisconnect( s.getHandler() );
                    getActiveClients().remove( s.getHandler().getRemoteClientId() );
                } );

        AtomicBoolean isrunning=new AtomicBoolean();
        do {
            isrunning.set(false);
            tcpRemoteServers.forEach( (k, s) -> {
                if(s.running.get()) {
                    isrunning.set(true);
                }
            });
        } while(isrunning.get());
        tcpRemoteServers.clear();
    }

    @Override protected void execute() {
        while (isRunning()) {
            waitfor( 10000 );
            getTcpRemoteServers().forEach( s -> s.getHandler().printWork() );
        }
    }
}
