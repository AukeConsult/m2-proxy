package m2.proxy.server.tcp;

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


// handle sessions access to remote
// session start with a logon request that return a walid accesskey
// accessKey is used in url for identify client to forward

public class SessionsHandler {
    private static final Logger log = LoggerFactory.getLogger( SessionsHandler.class );

    private final Map<String, ClientSession> clientSessions = new ConcurrentHashMap<>();
    public Map<String, ClientSession> getClientSessions() {
        return clientSessions;
    }

    private final TcpServer tcpServer;
    public SessionsHandler(TcpServer tcpServer) { this.tcpServer=tcpServer; }

    public Optional<MessageOuterClass.Logon> logon (
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

                // open a tcp session to remote proxy and send logon request
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
                    log.info( "Got accessPath: {}, client: {}", logon.get().getAccessKey(), remoteClient );
                    ClientSession a = new ClientSession( logon.get().getAccessKey(), tcpServer.myId(), session );
                    clientSessions.put( a.getAccessKey(), a );
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

    public Optional<ByteString> forward(
            String accessKey,
            String path,
            String remoteAddress,
            String accessToken,
            String agent,
            String request
    ) throws TcpException {

        ClientSession clientSession = clientSessions.getOrDefault( accessKey, null );
        if (clientSession !=null) {

            log.info( "client: {}, Remote Forward {}", clientSession.getClientId(), path );

            MessageOuterClass.Http m = MessageOuterClass.Http.newBuilder()
                    .setAccessPath( accessKey )
                    .setRemoteAddress( remoteAddress )
                    .setAccessToken( accessToken )
                    .setAgent( agent )
                    .setPath( path )
                    .setRequest( ByteString.copyFromUtf8( request ) )
                    .build();

            ByteString ret = clientSession.getSessionHandler().sendRequest(
                    "", m.toByteString(), MessageOuterClass.RequestType.HTTP
            );

            if (!ret.isEmpty()) {
                try {
                    MessageOuterClass.HttpReply reply = MessageOuterClass.HttpReply.parseFrom( ret );
                    if (reply.getOkLogon()) {
                        log.warn( "GOT REPLY client: {}", clientSession.getClientId() );
                        return Optional.of( reply.getReply() );
                    } else {
                        log.warn( "REJECTED REQUEST client: {}", clientSession.getClientId() );
                        throw new TcpException( ProxyStatus.REJECTED, "rejected remote request" );
                    }
                } catch (InvalidProtocolBufferException e) {
                    throw new TcpException( ProxyStatus.FAIL, e.getMessage() );
                }
            }
        }
        throw new TcpException( ProxyStatus.REJECTED, "Cant find client for access Path: " + accessKey );
    }
}
