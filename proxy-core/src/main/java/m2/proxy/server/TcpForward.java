package m2.proxy.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;
import m2.proxy.common.*;
import m2.proxy.proto.MessageOuterClass.HttpReply;
import m2.proxy.proto.MessageOuterClass.Message;
import m2.proxy.proto.MessageOuterClass.RequestType;
import m2.proxy.tcp.server.TcpServer;
import m2.proxy.tcp.handlers.ConnectionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;

import java.io.IOException;
import java.util.Optional;

public class TcpForward extends TcpServer implements Service {
    private static final Logger log = LoggerFactory.getLogger( TcpForward.class );

    private final RawHttp http = new RawHttp();
    private final HttpHelper httpHelper = new HttpHelper();

    private final int timeOut;

    public TcpForward(int tcpPort, int timeOut) {
        super( tcpPort, Network.localAddress(), null );
        this.timeOut = timeOut;
    }

    private Optional<RawHttpResponse<?>> logonRequest(RawHttpRequest request) {

        try {

            String remoteClientId = request.getUri().getPath().replace( "/logon/", "" );
            log.info( "Logon to client: {}", myId );

            String remoteAddress = "";
            if (request.getSenderAddress().isPresent()) {
                remoteAddress = request.getSenderAddress().get().getHostAddress();
            }

            RawHttpRequest resp = request.eagerly();
            String json = resp.getBody().map( httpHelper.decodeBody() ).orElse( "{}" );

            JsonObject jsonObj = new Gson().fromJson( json, JsonObject.class );
            String accessToken = request.getHeaders().get( "Access-Token" ).toString();
            String agent = request.getHeaders().get( "Agent" ).toString();

            Optional<String> accessPath = getTcpSession().logon(
                    remoteClientId,
                    remoteAddress,
                    jsonObj.get( "userid" ).getAsString(),
                    jsonObj.get( "password" ).getAsString(),
                    accessToken,
                    agent
            );
            if(accessPath.isPresent()) {
                return Optional.of( http.parseResponse(
                        httpHelper.errReply( 200, ProxyStatus.OK, "" )
                ) );
            } else {
                log.warn( "Rejected client: {}", myId );
                return Optional.of( http.parseResponse(
                        httpHelper.errReply( 403, ProxyStatus.REJECTED, "" )
                ) );
            }
        } catch (IOException | TcpException e) {
            log.warn( "Exception client: {}, err: {}", myId, e.getMessage() );
            return Optional.of( http.parseResponse(
                    httpHelper.errReply( 404, ProxyStatus.FAIL, e.getMessage() )
            ) );
        }
    }

    private Optional<RawHttpResponse<?>> forward(RawHttpRequest request) {

        Optional<String> accessPath = httpHelper.getAccessPath( request );
        if (accessPath.isPresent()) {
            Optional<RawHttpRequest> requestOut = httpHelper.forward( accessPath.get(), request );
            if (requestOut.isPresent() && getTcpSession().getAccessPaths().containsKey( accessPath.get() )) {

                try {

                    log.info( "forward key: {}", accessPath.get() );

                    String remoteAddress = "";
                    if (request.getSenderAddress().isPresent()) {
                        remoteAddress = request.getSenderAddress().get().getHostAddress();
                    }
                    String accessToken = request.getHeaders().get( "AccessToken" ).toString();
                    String agent = request.getHeaders().get( "Agent" ).toString();
                    String path = requestOut.get().getStartLine().getUri().getPath();

                    Optional<ByteString> ret = getTcpSession().forwardHttp(
                            accessPath.get(),
                            path,
                            remoteAddress,
                            accessToken,
                            agent,
                            requestOut.get().eagerly().toString(),
                            timeOut
                    );

                    if (ret.isPresent()) {
                        HttpReply reply = HttpReply.parseFrom( ret.get() );
                        if (reply.getOkLogon()) {
                            log.warn( "GOT REPLY client: {}", myId );
                            return Optional.of( http.parseResponse( reply.getReply().toStringUtf8() ) );
                        } else {
                            log.warn( "REJECTED REQUEST client: {}", myId );
                            return Optional.of( http.parseResponse(
                                    httpHelper.errReply( 403, ProxyStatus.REJECTED, "no access" )
                            ) );
                        }
                    }

                } catch (TcpException e) {
                    log.warn( "client: {}, err: {}", myId, e.getMessage() );
                    if(e.getStatus().equals( ProxyStatus.REJECTED )) {
                        return Optional.of(
                                http.parseResponse(
                                        httpHelper.errReply( 403, e.getStatus(), e.getMessage() )
                                )
                        );
                    } else if(e.getStatus().equals( ProxyStatus.FAIL )){
                        return Optional.of(
                                http.parseResponse(
                                        httpHelper.errReply( 500, e.getStatus(), e.getMessage() )
                                )
                        );
                    } else {
                        return Optional.of(
                                http.parseResponse(
                                        httpHelper.errReply( 404, e.getStatus(), e.getMessage() )
                                )
                        );
                    }
                } catch (IOException e) {
                    return Optional.of(
                            http.parseResponse(
                                    httpHelper.errReply( 500, ProxyStatus.FAIL, e.getMessage() )
                            )
                    );
                }
            }
        }
        return Optional.empty();
    }

    public Optional<RawHttpResponse<?>> handleHttp(RawHttpRequest request) throws TcpException {
        if (request.getMethod().equals( "PUT" ) && request.getUri().getPath().startsWith( "/logon" )) {
            return logonRequest( request );
        } else {
            return forward( request );
        }
    }

    @Override
    public ConnectionHandler setConnectionHandler() {
        return new ConnectionHandler() {
            @Override
            protected void onMessageIn(Message m) { }
            @Override
            protected void onMessageOut(Message m) { }
            @Override
            protected void onConnect(String ClientId, String remoteAddress) { }
            @Override protected void onDisonnect(String ClientId, String remoteAddress) { }
            @Override
            public void onRequest(long sessionId, long requestId, RequestType type, String destination, ByteString request) { }
        };
    }

    private Service service;
    @Override
    public Service getService() { return service; }
    @Override
    public void setService(Service service) { this.service = service; }
}
