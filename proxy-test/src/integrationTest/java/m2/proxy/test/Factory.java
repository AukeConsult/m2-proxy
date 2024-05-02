package m2.proxy.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import m2.proxy.common.DirectForward;
import m2.proxy.common.DirectSite;
import m2.proxy.common.LocalForward;
import m2.proxy.common.ContentResult;
import m2.proxy.server.ProxyServer;
import m2.proxy.server.TcpForward;
import m2.proxy.tcp.server.AccessPath;
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
        DirectForward directForward = new DirectForward();

        TcpForward tcpForward = new TcpForward( tcpPort, 10000 );
        tcpForward.getTcpSession().getAccessPaths().put( "test1", new AccessPath( "test1", "client1" ) );
        tcpForward.getTcpSession().getAccessPaths().put( "test2", new AccessPath( "test2", "client2" ) );

        DirectSite spark = new DirectSite( "/spark", "localhost:9999" );
        directForward.sites.put( spark.getPath(), spark );

        LocalForward localForward = new LocalForward() {

            @Override
            protected Optional<ContentResult> onHandlePath(
                    String verb,
                    String path,
                    Map<String, String> headers,
                    String contentType,
                    String body
            ) {
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

        return new ProxyServer(
                serverPort,
                directForward,
                tcpForward,
                localForward
        );
    }

    static Optional<EagerHttpResponse<?>> getRest(int port, String path) {
        try (TcpRawHttpClient client = new TcpRawHttpClient()) {
            log.info("get send: {}",path);
            EagerHttpResponse<?> rawResponse = client.send( new RawHttp().parseRequest( String.format( "GET localhost:%d%s HTTP/1.1", port, path ) ) ).eagerly();
            log.info("return: {}",rawResponse.getStartLine().getReason());
            return Optional.of(rawResponse);
        } catch (IOException e) {
            log.error("Request: {}, IOException", path, e.getMessage());
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
            log.error("Request: {}, IOException", path, e.getMessage());
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
            log.error("Request: {}, IOException", path, e.getMessage());
            return Optional.empty();
        }
    }

    static Optional<EagerHttpResponse<?>> putRest(int port, String path, String body) {
        try (TcpRawHttpClient client = new TcpRawHttpClient()) {
            String req = String.format( "PUT localhost:%d%s HTTP/1.1\r\nUser-Agent: RawHTTP", port, path );
            if (body != null) {
                req = req + "\r\n"
                        + "Content-Length: " + body.length() + "\r\n"
                        + "Content-Type: text/plain\r\n"
                        + "Accept: text/plain\r\n"
                        + "\r\n"
                        + body;

            }
            log.info("put send: {}, body: {}",path,body);
            EagerHttpResponse<?> rawResponse = client.send( new RawHttp().parseRequest( req ) ).eagerly();
            return Optional.of(rawResponse);
        } catch (IOException e) {
            log.error("Request: {}, IOException", path, e.getMessage());
            return Optional.empty();
        }
    }

}
