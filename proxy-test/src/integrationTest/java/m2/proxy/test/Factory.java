package m2.proxy.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import m2.proxy.common.HttpException;
import m2.proxy.server.remote.RemoteForward;
import m2.proxy.server.remote.RemoteSite;
import m2.proxy.common.ContentResult;
import m2.proxy.server.tcp.ClientSession;
import m2.proxy.server.tcp.TcpForward;
import m2.proxy.server.ProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rawhttp.core.EagerHttpResponse;
import rawhttp.core.RawHttp;
import rawhttp.core.client.TcpRawHttpClient;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;


public class Factory {

    private static final Logger log = LoggerFactory.getLogger( Factory.class );

    public static ProxyServer createServer(int serverPort, int tcpPort) {
        RemoteForward remoteForward = new RemoteForward();

        TcpForward tcpForward = new TcpForward( tcpPort, 10000 );
        tcpForward.getAccessSession().getClientSessions().put( "test1", new ClientSession( "test1", "client1" ,null) );
        tcpForward.getAccessSession().getClientSessions().put( "test2", new ClientSession( "test2", "client2" ,null) );

        RemoteSite spark = new RemoteSite( "/spark", "localhost:9999" );
        remoteForward.sites.put( spark.getPath(), spark );

        return new ProxyServer(
                serverPort,
                remoteForward,
                tcpForward
        ) {
            @Override protected Optional<ContentResult> onHandlePath(String verb, String path, Map<String, String> headers, String contentType, String body)
                    throws HttpException {

                if (verb.equals( "GET" )) {
                    switch (path) {
                        case "/local/hello" -> {
                            log.info( "local hello: {}", body );
                            return Optional.of( new ContentResult( body ) );
                        }
                        case "/local/json" -> {
                            if (contentType.equals( "application/json" )) {
                                JsonObject jsonBody = JsonParser.parseString( body )
                                        .getAsJsonObject();
                                return Optional.of( new ContentResult( jsonBody ) );
                            } else if (contentType.equals( "text/plain" )) {
                                JsonObject json = new JsonObject();
                                json.addProperty( "data", body );
                                log.info( "local: {}", body );
                                return Optional.of( new ContentResult( json ) );
                            }
                        }
                        case "/local" -> {
                            log.info( "local: {}", body );
                            return Optional.of( new ContentResult( "local" ) );
                        }
                    }
                }
                return Optional.empty();
            }
        };
    }

    static Optional<EagerHttpResponse<?>> getRest(int port, String path) {
        try (TcpRawHttpClient client = new TcpRawHttpClient()) {
            log.info("get send: {}",path);
            EagerHttpResponse<?> rawResponse = client.send( new RawHttp().parseRequest( String.format( "GET localhost:%d%s HTTP/1.1", port, path ) ) ).eagerly();
            log.info("return: {}",rawResponse.getStartLine().getReason());
            return Optional.of(rawResponse);
        } catch (IOException e) {
            log.error("Request: {}, IOException: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    static Optional<EagerHttpResponse<?>> getRest(int port, String path, String body) {
        try (TcpRawHttpClient client = new TcpRawHttpClient()) {
            String req = String.format( "GET localhost:%d%s HTTP/1.1\r\nUser-Agent: RawHTTP", port, path );
            if (body != null) {
                req = req + "\r\n"
                        + "Content-Length: " + body.length() + "\r\n"
                        + "Content-Type: text/plain\r\n"
                        + "Accept: text/plain\r\n"
                        + "\r\n"
                        + body;

            }
            log.info("get send: {}, body: {}",path,body);
            EagerHttpResponse<?> rawResponse = client.send( new RawHttp().parseRequest( req ) ).eagerly();
            log.info("return: {}",rawResponse.getStartLine().getReason());
            return Optional.of(rawResponse);
        } catch (IOException e) {
            log.error("Request: {}, IOException: {}", path, e.getMessage());
            return Optional.empty();
        }

    }

    static Optional<EagerHttpResponse<?>> getRest(int port, String path, JsonObject body) {
        try (TcpRawHttpClient client = new TcpRawHttpClient()) {
            String req = String.format( "GET localhost:%d%s HTTP/1.1\r\nUser-Agent: RawHTTP", port, path );
            if (body != null) {
                req = req + "\r\n"
                        + "Content-Length: " + body.toString().length() + "\r\n"
                        + "Content-Type: application/json\r\n"
                        + "Accept: application/json\r\n"
                        + "\r\n"
                        + body;
            }
            log.info("get send: {}, body: {}",path,body);
            EagerHttpResponse<?> rawResponse = client.send( new RawHttp().parseRequest( req ) ).eagerly();
            return Optional.of(rawResponse);
        } catch (IOException e) {
            log.error("Request: {}, IOException: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    static Optional<EagerHttpResponse<?>> putRest(int port, String body) {
        try (TcpRawHttpClient client = new TcpRawHttpClient()) {
            String req = String.format( "PUT localhost:%d%s HTTP/1.1\r\nUser-Agent: RawHTTP", port, "/spark/echo" );
            if (body != null) {
                req = req + "\r\n"
                        + "Content-Length: " + body.length() + "\r\n"
                        + "Content-Type: text/plain\r\n"
                        + "Accept: text/plain\r\n"
                        + "\r\n"
                        + body;

            }
            log.info("put send: {}, body: {}", "/spark/echo",body);
            EagerHttpResponse<?> rawResponse = client.send( new RawHttp().parseRequest( req ) ).eagerly();
            return Optional.of(rawResponse);
        } catch (IOException e) {
            log.error("Request: {}, IOException: {}", "/spark/echo", e.getMessage());
            return Optional.empty();
        }
    }

}
