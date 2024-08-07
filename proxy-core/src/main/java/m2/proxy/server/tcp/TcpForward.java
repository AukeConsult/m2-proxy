package m2.proxy.server.tcp;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;
import m2.proxy.common.*;
import m2.proxy.proto.MessageOuterClass.FunctionStatus;
import m2.proxy.proto.MessageOuterClass.Logon;
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

// send request TCP client
public class TcpForward extends TcpServer implements Service {
    private static final Logger log = LoggerFactory.getLogger( TcpForward.class );

    private final RawHttp http = new RawHttp();
    private final HttpHelper httpHelper = new HttpHelper();

    private final int timeOut;

    public TcpForward(int tcpPort, int timeOut) {
        super( tcpPort, Network.localAddress(), null );
        this.timeOut = timeOut;
    }

    // send a logon request
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

            Optional<Logon> logon = getAccessSession().logon(
                    remoteClientId,
                    remoteAddress,
                    jsonObj.get( "userId" ).getAsString(),
                    jsonObj.get( "passWord" ).getAsString(),
                    accessToken,
                    agent
            );

            if(logon.isPresent() && logon.get().getStatus().equals( FunctionStatus.OK_LOGON )) {

                JsonObject jsonRet = new JsonObject();
                jsonRet.addProperty( "accessKey", logon.get().getAccessKey() );
                jsonRet.addProperty( "userId", logon.get().getUserId() );
                jsonRet.addProperty( "passWord", logon.get().getPassWord() );
                jsonRet.addProperty( "accessToken", logon.get().getAccessToken() );
                jsonRet.addProperty( "status", logon.get().getStatus().toString());
                jsonRet.addProperty( "message", logon.get().getMessage());

                return Optional.of( http.parseResponse(
                        httpHelper.reply( 200, ProxyStatus.OK, jsonRet )
                ) );

            } else {
                log.warn( "Rejected client: {}", myId );
                JsonObject jsonRet = new JsonObject();
                if(logon.isPresent()) {
                    jsonRet.addProperty( "status", logon.get().getStatus().toString());
                    jsonRet.addProperty( "message", logon.get().getMessage());
                }
                return Optional.of( http.parseResponse(
                        httpHelper.reply( 403, ProxyStatus.REJECTED )
                ) );
            }
        } catch (IOException | TcpException e) {
            log.warn( "Exception client: {}, err: {}", myId, e.getMessage() );
            JsonObject jsonRet = new JsonObject();
            jsonRet.addProperty( "status", ProxyStatus.FAIL.toString());
            jsonRet.addProperty( "message", e.getMessage());
            return Optional.of( http.parseResponse(
                    httpHelper.reply( 404, ProxyStatus.FAIL, jsonRet )
            ) );
        }
    }

    private Optional<RawHttpResponse<?>> forward(RawHttpRequest request) {

        Optional<String> accessPath = httpHelper.getAccessPath( request );
        if (accessPath.isPresent()) {
            Optional<RawHttpRequest> requestOut = httpHelper.pathAccessKey( accessPath.get(), request );
            if (requestOut.isPresent() && getAccessSession().getClientSessions().containsKey( accessPath.get() )) {

                try {

                    log.info( "forward key: {}", accessPath.get() );

                    String remoteAddress = "";
                    if (request.getSenderAddress().isPresent()) {
                        remoteAddress = request.getSenderAddress().get().getHostAddress();
                    }
                    String accessToken = request.getHeaders().get( "AccessToken" ).toString();
                    String agent = request.getHeaders().get( "Agent" ).toString();
                    String path = requestOut.get().getStartLine().getUri().getPath();

                    Optional<ByteString> ret = getAccessSession().forward(
                            accessPath.get(),
                            path,
                            remoteAddress,
                            accessToken,
                            agent,
                            requestOut.get().eagerly().toString()
                    );

                    if (ret.isPresent()) {
                        HttpReply reply = HttpReply.parseFrom( ret.get() );
                        if (reply.getOkLogon()) {
                            log.warn( "GOT REPLY client: {}", myId );
                            return Optional.of( http.parseResponse( reply.getReply().toStringUtf8() ) );
                        } else {
                            log.warn( "REJECTED REQUEST client: {}", myId );
                            return Optional.of( http.parseResponse(
                                    httpHelper.reply( 403, ProxyStatus.REJECTED, "no access" )
                            ) );
                        }
                    }
                } catch (TcpException e) {
                    log.warn( "client: {}, err: {}", myId, e.getMessage() );
                    if(e.getStatus().equals( ProxyStatus.REJECTED )) {
                        return Optional.of(
                                http.parseResponse(
                                        httpHelper.reply( 403, e.getStatus(), e.getMessage() )
                                )
                        );
                    } else if(e.getStatus().equals( ProxyStatus.FAIL )){
                        return Optional.of(
                                http.parseResponse(
                                        httpHelper.reply( 500, e.getStatus(), e.getMessage() )
                                )
                        );
                    } else {
                        return Optional.of(
                                http.parseResponse(
                                        httpHelper.reply( 404, e.getStatus(), e.getMessage() )
                                )
                        );
                    }
                } catch (IOException e) {
                    return Optional.of(
                            http.parseResponse(
                                    httpHelper.reply( 500, ProxyStatus.FAIL, e.getMessage() )
                            )
                    );
                }
            }
        }
        return Optional.empty();
    }

    // forward request using TCP
    public Optional<RawHttpResponse<?>> handleHttp(RawHttpRequest request) throws TcpException {
        if (request.getMethod().equals( "PUT" ) && request.getUri().getPath().startsWith( "/logon" )) {
            // send logon to a proxy
            return logonRequest( request );
        } else {
            return forward( request );
        }
    }

    @Override public ConnectionHandler setConnectionHandler() {
        return new ConnectionHandler() {
            @Override protected void handlerOnMessageIn(Message m) { }
            @Override protected void handlerOnMessageOut(Message m) { }
            @Override protected void handlerOnConnect(String ClientId, String remoteAddress) { }
            @Override protected void handlerOnDisonnect(String ClientId, String remoteAddress) { }
            @Override public void notifyOnRequest(long sessionId, long requestId, RequestType type, String destination, ByteString request) { }
        };
    }

    private Service service;
    @Override public Service getService() { return service; }
    @Override public void setService(Service service) { this.service = service; }
}
