package m2.proxy.client;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import m2.proxy.common.*;
import m2.proxy.proto.MessageOuterClass.HttpReply;
import m2.proxy.proto.MessageOuterClass.Http;
import m2.proxy.proto.MessageOuterClass.Message;
import m2.proxy.proto.MessageOuterClass.RequestType;
import m2.proxy.tcp.client.TcpClient;
import m2.proxy.tcp.handlers.ConnectionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;

import java.util.Optional;

public class ProxyClient extends TcpClient implements Service {

    private static final Logger log = LoggerFactory.getLogger( ProxyClient.class );

    private final DirectForward directForward;
    private final LocalForward localForward;
    private final ClientSite clientSite;
    private final AccessControl accessControl;

    public ProxyClient(
            String clientId,
            String serverAddr,
            int tcpPort,
            int sitePort,
            AccessControl accessControl,
            DirectForward directForward,
            LocalForward localForward
    ) {
        super( clientId, serverAddr, tcpPort, Network.localAddress() );
        this.accessControl = accessControl;
        this.directForward = directForward;
        this.localForward = localForward;
        this.accessControl.setService( this );
        this.directForward.setService( this );
        this.localForward.setService( this );
        this.clientSite = new ClientSite( this, sitePort, directForward, localForward );
        this.clientSite.getMetrics().setId( clientId );
    }

    @Override protected boolean onCheckAccess(String accessPath, String remoteAddress, String accessToken, String agent) {
        return false;
    }
    @Override protected Optional<String> onSetAccess(String userId, String passWord, String remoteAddress, String accessToken, String agent) {
        return Optional.of("test");
    }
    @Override
    public ConnectionHandler setConnectionHandler() {
        return new ConnectionHandler() {
            final HttpHelper httpHelper = new HttpHelper();
            @Override
            protected void handlerOnMessageIn(Message m) { }
            @Override
            protected void handlerOnMessageOut(Message m) { }
            @Override
            protected void handlerOnConnect(String ClientId, String remoteAddress) { }
            @Override protected void handlerOnDisonnect(String ClientId, String remoteAddress) {}
            @Override
            protected void notifyOnRequest(long sessionId, long requestId, RequestType requestType, String address, ByteString requestBytes) {
                getTcpService().getTaskPool().execute( () -> {

                    try {

                        if (requestType == RequestType.HTTP) {

                            Http m = Http.parseFrom( requestBytes );

                            RawHttpRequest request = httpHelper.parseRequest( m.getRequest().toStringUtf8() );
                            Optional<RawHttpResponse<?>> ret = directForward.handleHttp( request );
                            if (ret.isEmpty()) {
                                ret = localForward.handleHttp( request );
                            }

                            if (ret.isPresent()) {

                                log.info( "Reply -> session: {}, id: {}, type: {}", sessionId, requestId, requestType );
                                reply( sessionId, requestId, requestType,
                                        HttpReply.newBuilder()
                                                .setOkLogon( true )
                                                .setReply(
                                                        ByteString.copyFromUtf8( ret.get().toString() )
                                                )
                                                .build()
                                                .toByteString()
                                );

                            } else {

                                reply( sessionId, requestId, requestType,
                                        HttpReply.newBuilder()
                                                .setOkLogon( true )
                                                .setReply(
                                                        ByteString.copyFromUtf8(
                                                                httpHelper.reply( 404,
                                                                        ProxyStatus.NOTFOUND,
                                                                        request.getUri().getPath() )
                                                        )
                                                )
                                                .build()
                                                .toByteString()
                                );

                            }

                        }
                    } catch (HttpException | InvalidProtocolBufferException e) {
                        log.warn( "Error request: {}", e.getMessage() );
                        ByteString reply = ByteString.copyFromUtf8(
                                httpHelper.reply( 404, ProxyStatus.FAIL, e.getMessage() )
                        );
                        reply( sessionId, requestId, requestType, reply );
                    }
                } );
            }
        };
    }

    @Override
    public void onStart() {
        super.onStart();
        log.info( "Proxy clientId {}, start on site port: {}, proxy port: {}:{}",
                myId(),
                clientSite.getSitePort() , connectAddress(), connectPort() );
        clientSite.start();
    }

    @Override
    public void onStop() {
        super.onStop();
        log.info( "Proxy clientId {}, stopped", myId() );
        clientSite.stop();
        clientSite.getMetrics().printLog();
    }

    @Override
    protected void execute() {
        while (isRunning()) {
            clientSite.getMetrics().printLog();
            waitfor( 10000 );
        }
    }

    private Service service;
    @Override
    public Service getService() { return service; }
    @Override
    public void setService(Service service) { this.service = service; }
}
