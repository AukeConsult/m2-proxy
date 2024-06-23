package m2.proxy.server;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import m2.proxy.common.ProxyStatus;
import m2.proxy.common.TcpException;
import m2.proxy.proto.MessageOuterClass;
import m2.proxy.tcp.handlers.ConnectionHandler;
import m2.proxy.tcp.handlers.SessionHandler;
import m2.proxy.tcp.server.TcpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class AccessSession {
    private static final Logger log = LoggerFactory.getLogger( AccessSession.class );

    private final Map<String, Access> accessPaths = new ConcurrentHashMap<>();
    public Map<String, Access> getAccessPaths() {
        return accessPaths;
    }

    private final TcpServer tcpServer;
    public AccessSession(TcpServer tcpServer) { this.tcpServer=tcpServer; }

    public Optional<MessageOuterClass.Logon> logon(
            String remoteClient,
            String remoteAddress,
            String userId,
            String passWord,
            String accessToken,
            String agent
    ) throws TcpException {

        try {

            if (tcpServer.getActiveClients().containsKey( remoteClient ) &&
                    tcpServer.getActiveClients().get( remoteClient ).isOpen()) {

                ConnectionHandler client = tcpServer.getActiveClients().get( remoteClient );
                SessionHandler session = client.openSession( remoteAddress, 10000);
                Optional<MessageOuterClass.Logon> logon = session
                        .logon(
                            userId,
                            passWord,
                            remoteAddress,
                            accessToken,
                            agent
                        );

                if (logon.isPresent()) {
                    log.info( "Got accessPath: {}, client: {}", logon.get().getAccessPath(), remoteClient );
                    Access a = new Access( logon.get().getAccessPath(), tcpServer.myId(),session );
                    accessPaths.put( a.getAccessPath(), a );
                    return logon;
                } else {
                    log.warn( "Rejected client: {}", remoteClient );
                    throw new TcpException( ProxyStatus.REJECTED, "cant find client" );
                }
            }
            return Optional.empty();

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

        Access access = accessPaths.getOrDefault( accessPath, null );
        if (access!=null) {

            log.info( "client: {}, Remote Forward {}", access.getClientId(), path );

            MessageOuterClass.Http m = MessageOuterClass.Http.newBuilder()
                    .setAccessPath( accessPath )
                    .setRemoteAddress( remoteAddress )
                    .setAccessToken( accessToken )
                    .setAgent( agent )
                    .setPath( path )
                    .setRequest( ByteString.copyFromUtf8( request ) )
                    .build();

            ByteString ret = access.getSessionHandler().sendRequest(
                    "", m.toByteString(), MessageOuterClass.RequestType.HTTP
            );

            if (!ret.isEmpty()) {
                try {
                    MessageOuterClass.HttpReply reply = MessageOuterClass.HttpReply.parseFrom( ret );
                    if (reply.getOkLogon()) {
                        log.warn( "GOT REPLY client: {}", access.getClientId() );
                        return Optional.of( reply.getReply() );
                    } else {
                        log.warn( "REJECTED REQUEST client: {}", access.getClientId() );
                        throw new TcpException( ProxyStatus.REJECTED, "rejected remote request" );
                    }
                } catch (InvalidProtocolBufferException e) {
                    throw new TcpException( ProxyStatus.FAIL, e.getMessage() );
                }
            }
        }
        throw new TcpException( ProxyStatus.REJECTED, "Cant find client for access Path: " + accessPath );
    }
}
