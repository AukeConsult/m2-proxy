package m2.proxy.tcp.server;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import m2.proxy.common.ProxyStatus;
import m2.proxy.common.TcpException;
import m2.proxy.proto.MessageOuterClass;
import m2.proxy.tcp.handlers.ConnectionHandler;
import m2.proxy.tcp.handlers.SessionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TcpSession {
    private static final Logger log = LoggerFactory.getLogger( TcpSession.class );

    private final Map<String, AccessPath> accessPaths = new ConcurrentHashMap<>();
    public Map<String, AccessPath> getAccessPaths() {
        return accessPaths;
    }

    private final TcpServer tcpServer;
    public TcpSession(TcpServer tcpServer) { this.tcpServer=tcpServer; }

    protected Optional<SessionHandler> getSession(String clientId, String remoteAddress) {

        if (tcpServer.getActiveClients().containsKey( clientId ) && tcpServer.getActiveClients().get( clientId ).isOpen()) {
            ConnectionHandler client = tcpServer.getActiveClients().get( clientId );
            long sessionId = remoteAddress != null ?
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
                    AccessPath a = new AccessPath( accessPath.get(), tcpServer.myId() );
                    accessPaths.put( a.getAccessPath(), a );
                    return accessPath;
                } else {
                    log.warn( "Rejected client: {}", remoteClient );
                    throw new TcpException( ProxyStatus.REJECTED, "cant fond client" );
                }
            } else {
                log.warn( "{} -> Can not find client: {}", tcpServer.myId(), remoteClient );
                throw new TcpException( ProxyStatus.NOTFOUND, "cant fond client" );
            }
        } catch (TcpException e) {
            throw new TcpException( e.getStatus(), e.getMessage() );
        }
    }

    public Optional<ByteString> forwardHttp(
            String accessPath,
            String path,
            String remoteAddress,
            String accessToken,
            String agent,
            String request,
            int timeOut
    ) throws TcpException {

        String remoteClientId = accessPaths.getOrDefault(
                accessPath,
                new AccessPath( "", "" )
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
                        return Optional.of( reply.getReply() );
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
}
