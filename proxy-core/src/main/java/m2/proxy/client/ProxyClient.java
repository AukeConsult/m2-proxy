package m2.proxy.client;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import m2.proxy.common.HttpException;
import m2.proxy.common.HttpHelper;
import m2.proxy.common.Network;
import m2.proxy.common.ProxyStatus;
import m2.proxy.proto.MessageOuterClass.Logon;
import m2.proxy.proto.MessageOuterClass.Message;
import m2.proxy.proto.MessageOuterClass.RequestType;
import m2.proxy.tcp.TcpBase;
import m2.proxy.tcp.TcpBaseClientBase;
import m2.proxy.tcp.handlers.ConnectionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;

import java.util.Optional;
import java.util.Random;

public class ProxyClient extends TcpBaseClientBase {

    private static final Logger log = LoggerFactory.getLogger( ProxyClient.class );

    private final DirectForward directForward;
    private final LocalForward localForward;

    public ProxyClient(
            String clientId, String serverAddr, int tcpPort, DirectForward directForward, LocalForward localForward
    ) {
        super( clientId, serverAddr, tcpPort, Network.localAddress() ); this.directForward = directForward;
        this.localForward = localForward;
    }

    @Override
    public ConnectionHandler setConnectionHandler() {
        return new ConnectionHandler() {
            final HttpHelper httpHelper = new HttpHelper();
            @Override
            protected void onMessageIn(Message m) { }
            @Override
            protected void onMessageOut(Message m) { }
            @Override
            protected void onConnect(String ClientId, String remoteAddress) { }
            @Override
            protected void onDisconnect(String ClientId) { }
            @Override
            protected void onRequest(long sessionId, long requestId, RequestType type, String address, ByteString requestBytes) {
                getServer().getTaskPool().execute( () -> {
                    try {
                        if (type == RequestType.HTTP) {
                            RawHttpRequest request = httpHelper.parseRequest( requestBytes.toStringUtf8() );
                            Optional<RawHttpResponse<?>> ret = directForward.handleHttp( request );
                            if (ret.isEmpty()) {
                                ret = localForward.handleHttp( request );
                            }
                            Thread.sleep( TcpBase.rnd.nextInt( 10 ) + 1 );

                            if (ret.isPresent()) {
                                ByteString reply = ByteString.copyFromUtf8( ret.get().toString() );
                                log.info( "Reply -> session: {}, id: {}, type: {}", sessionId, requestId, type );
                                reply( sessionId, requestId, type, reply );
                            } else {
                                ByteString reply = ByteString.copyFromUtf8(
                                        httpHelper.errReply( 404, ProxyStatus.NOTFOUND, request.getUri().getPath() ).toString() );
                                reply( sessionId, requestId, type, reply );
                            }
                        } else if (type == RequestType.LOGON) {
                            Logon logon = Logon.parseFrom( requestBytes );
                            if (logon.getClientId().equals( getClientId().get() )) {
                                Logon replyMessage = Logon.newBuilder()
                                        .setClientId( logon.getClientId() )
                                        .setAccessPath( logon.getClientId() + new Random().nextInt( 10 ) )
                                        .setRemoteAddress( logon.getRemoteAddress() )
                                        .setOkLogon( true )
                                        .build();
                                reply( sessionId, requestId, type, replyMessage.toByteString() );
                            }
                        }
                    } catch (HttpException | InterruptedException | InvalidProtocolBufferException e) {
                        log.warn( "Error request: {}", e.getMessage() );
                        ByteString reply = ByteString.copyFromUtf8(
                                httpHelper.errReply( 404, ProxyStatus.FAIL, e.getMessage() )
                        );
                        reply( sessionId, requestId, type, reply );
                    }
                } );
            }
        };
    }
}
