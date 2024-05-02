package m2.proxy.tcp.server;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import m2.proxy.common.ProxyStatus;
import m2.proxy.common.TcpException;
import m2.proxy.proto.MessageOuterClass;
import m2.proxy.server.RemoteAccess;
import m2.proxy.tcp.TcpBase;
import m2.proxy.tcp.handlers.ConnectionHandler;
import m2.proxy.tcp.handlers.SessionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyPair;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

public abstract class TcpServer extends TcpBase {
    private static final Logger log = LoggerFactory.getLogger( TcpServer.class);

    private final Map<String, ConnectionHandler> clientHandles = new ConcurrentHashMap<>();
    public Map<String, ConnectionHandler> getClientHandles() { return clientHandles;}

    private final Map<String, RemoteAccess> access = new ConcurrentHashMap<>();
    public Map<String, RemoteAccess> getAccess() {
        return access;
    }

    public TcpServer(int serverPort, String localAddress, KeyPair rsaKey) {
        super("SERVER",localAddress,serverPort,localAddress,rsaKey);
        setLocalPort(serverPort);
        final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>(10000);
        taskPool = new ThreadPoolExecutor( 100, 5000, 30, TimeUnit.SECONDS, tasks );
    }

    protected Optional<SessionHandler> getSession(String clientId, String remoteAddress) {

        if (getActiveClients().containsKey( clientId ) && getActiveClients().get( clientId ).isOpen()) {
            ConnectionHandler client = getActiveClients().get( clientId );
            long sessionId = remoteAddress!=null ?
                    UUID.nameUUIDFromBytes(
                            remoteAddress.getBytes()
                    ).getMostSignificantBits()
                    : 1000L;
            if (!client.getSessions().containsKey( sessionId )) {
                client.openSession( new SessionHandler( sessionId ) {
                    @Override
                    public void onReceive(long requestId, ByteString reply) { }
                }, 10000 );
            }
            return Optional.of( client.getSessions().get( sessionId ) );
        } else {
            return Optional.empty();
        }
    }

    public Optional<String> logon(
            String remoteClient,
            String remoteAddress,
            String userId,
            String passWord,
            String accessToken,
            String agent) throws TcpException {
        try {

            Optional<SessionHandler> session = getSession( remoteClient, remoteAddress );
            if (session.isPresent()) {

                Optional<String> accessPath = session.get().logon(
                        userId,
                        passWord,
                        remoteAddress,
                        accessToken,
                        agent );

                if (accessPath.isPresent()) {
                    log.info( "Got accessPath: {}, client: {}", accessPath.get(), remoteClient );
                    RemoteAccess a = new RemoteAccess( accessPath.get(), getMyId() );
                    access.put( a.getAccessPath(), a );
                    return accessPath;
                } else {
                    log.warn( "Rejected client: {}", remoteClient );
                    throw new TcpException( ProxyStatus.REJECTED, "cant fond client" );
                }
            } else {
                log.warn( "{} -> Can not find client: {}", getMyId(), remoteClient );
                throw new TcpException( ProxyStatus.NOTFOUND, "cant fond client" );
            }

        } catch (TcpException e) {
            throw new TcpException(e.getStatus(),e.getMessage());
        }
    }

    protected Optional<ByteString> forwardHttp(
            String accessPath,
            String path,
            String remoteAddress,
            String accessToken,
            String agent,
            String request,
            int timeOut
    ) throws TcpException {

        String remoteClientId = getAccess().getOrDefault(
                accessPath,
                new RemoteAccess( "","" )
        ).getClientId();

        Optional<SessionHandler> session = getSession( remoteClientId, remoteAddress );
        if (session.isPresent()) {

            log.info( "client: {}, Remote Forward {}", remoteClientId, path );

            MessageOuterClass.Http m = MessageOuterClass.Http.newBuilder()
                    .setAccessPath( accessPath )
                    .setRemoteAddress( remoteAddress )
                    .setAccessToken( accessToken )
                    .setAgent( agent )
                    .setPath( path )
                    .setRequest( ByteString.copyFromUtf8( request ) )
                    .build();

            ByteString ret = session.get().sendRequest(
                    "", m.toByteString(), MessageOuterClass.RequestType.HTTP, timeOut
            );

            if (!ret.isEmpty()) {
                try {
                    MessageOuterClass.HttpReply reply = MessageOuterClass.HttpReply.parseFrom( ret );
                    if (reply.getOkLogon()) {
                        log.warn( "GOT REPLY client: {}", remoteClientId );
                        return Optional.of( reply.getReply() ) ;
                    } else {
                        log.warn( "REJECTED REQUEST client: {}", remoteClientId );
                        throw new TcpException( ProxyStatus.REJECTED, "rejected remote request" );
                    }
                } catch (InvalidProtocolBufferException e) {
                    throw new TcpException( ProxyStatus.FAIL, e.getMessage() );
                }
            }
        }
        throw new TcpException( ProxyStatus.REJECTED, "Cant find client: " + remoteClientId );
    }

    @Override
    public final void doDisconnect(ConnectionHandler handler) {

        // remove from active list
        clientHandles.remove(handler.getChannelId());
        handler.disconnectRemote();
        log.debug("{} -> disconnect ch: {}, client: {}, addr: {}",
                getMyId(),
                handler.getChannelId(),
                handler.getRemoteClientId(),
                handler.getRemotePublicAddress()
        );
    }

    @Override
    public final void onDisconnected(ConnectionHandler handler) {

//        // remove from active list
//        clientHandles.remove(handler.getChannelId());
//        handler.disconnectClose();
//
//        log.debug("{} -> disconnect ch: {}, client: {}, addr: {}",
//                getMyId(),
//                handler.getChannelId(),
//                handler.getRemoteClientId(),
//                handler.getRemotePublicAddress()
//        );

    }

    @Override
    public final void connect(ConnectionHandler handler) {
        handler.startPing(2);
    }

    @Override protected boolean onCheckAccess(String accessPath, String clientAddress, String accessToken, String agent) {
        return true;
    }
    @Override protected Optional<String> onSetAccess(String userId, String passWord, String clientAddress, String accessToken, String agent) {
        return Optional.empty();
    }


    private TcpServerWorker tcpServerWorker;

    @Override
    public final void onStart() {
        log.info("{} -> Starting netty server on -> {}:{} ", getMyId(), getLocalAddress(),getLocalPort());
        if(tcpServerWorker !=null) {
            tcpServerWorker.stopService();
        }
        tcpServerWorker = new TcpServerWorker(this);
        getExecutor().execute( tcpServerWorker );
    }

    @Override
    public final void onStop() {
        if(tcpServerWorker !=null) {
            tcpServerWorker.disconnectStopService();
        }
    }

    @Override
    final protected void execute() {
        while(isRunning()) {
            waitfor(10000);
        }
    }
}
