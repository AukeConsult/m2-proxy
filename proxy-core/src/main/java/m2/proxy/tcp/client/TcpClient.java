package m2.proxy.tcp.client;

import m2.proxy.tcp.TcpBase;
import m2.proxy.tcp.handlers.ConnectionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyPair;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings( "ALL" )
public abstract class TcpClient extends TcpBase {
    private static final Logger log = LoggerFactory.getLogger( TcpClient.class );

    // parameteres

    private String workerId;
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

    private void startWorker() {
        if(serviceRunning.get()) {
            TcpClientWorker tcpClientWorker = new TcpClientWorker(this, this.connectAddress, this.connectPort );
            getTaskPool().execute( tcpClientWorker );
            tcpRemoteServers.put( tcpClientWorker.getWorkerId(), tcpClientWorker );
        }
    }

    public TcpClient(String myId, String serverAddr, int serverPort, String localAddress, KeyPair rsaKey) {
        super(
                myId == null ? UUID.randomUUID().toString().substring( 0, 10 ) : myId,
                serverAddr, serverPort, localAddress, rsaKey
        );
        setLocalPort( 0 );
    }

    public TcpClient(String clientId, String serverAddr, int serverPort, String localAddress) {
        super(
                clientId == null ? UUID.randomUUID().toString().substring( 0, 10 ) : clientId,
                serverAddr, serverPort, localAddress, null
        );
        setLocalPort( 0 );
    }

    @Override public final void connect(ConnectionHandler handler) { }

    @Override public final void disconnectRemote(ConnectionHandler handler) {
        // stop everything and notify remote server
        handler.getConnectionWorker().disconnectRemote( true );
    }

    // got a disconnect from client side or client no longer answer
    @Override public final void gotClientDisconnect(ConnectionHandler handler, String cause) {
        // make thread to reconnect

        if(serviceRunning.get() && handler.getConnectionWorker().running.get()) {

            log.debug( "{} -> client start disconnect, cause: {}", myId(), cause);
            getTaskPool().execute( () -> {

                TcpClientWorker tcpClientWorker = (TcpClientWorker)handler.getConnectionWorker();
                // just stop everything without notify remote server
                tcpClientWorker.disconnectRemote( false );
                Thread.yield();

                log.debug( "{} -> Disconnected from server, {}, reconnect in: {} ms", myId(), cause, reconnectTime() );
                try {
                    Thread.sleep( reconnectTime() );
                } catch (InterruptedException ignored) {
                }

                if(serviceRunning.get()) {
                    startWorker( );
                    Thread.yield();
                }

            } );
        }
    }

    @Override protected void onStart() {
        log.info( "{} -> client start on {}:{}, connect to host -> {}:{}",myId(),
                localAddress(), localPort(), connectAddress, connectPort );
        startWorker( );
    }

    @Override protected void onStop() {

        log.info( "{} -> client stop on {}:{}, connect to host -> {}:{}",myId(),
                localAddress(), localPort(), connectAddress, connectPort );

        tcpRemoteServers
                .forEach( (k,s) -> {
                    s.getHandler().printWork();
                    disconnectRemote( s.getHandler() );
                } );

        AtomicBoolean isStopping=new AtomicBoolean();
        do {
            isStopping.set(false);
            tcpRemoteServers.forEach( (k, s) -> {
                if(s.running.get()) {
                    isStopping.set(true);
                }
            });
        } while(isStopping.get());
        tcpRemoteServers.clear();

    }

    @Override protected void execute() {
        while (isRunning()) {
            waitfor( 10000 );
            getTcpRemoteServers().forEach( s -> s.getHandler().printWork() );
        }
    }
}
