package m2.proxy.test;

import m2.proxy.client.DirectForward;
import m2.proxy.client.DirectSite;
import m2.proxy.client.LocalSite;
import m2.proxy.common.HttpException;
import m2.proxy.server.ProxyServer;
import m2.proxy.server.RemoteAccess;
import m2.proxy.server.RemoteForward;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rawhttp.core.EagerHttpResponse;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.client.TcpRawHttpClient;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import spark.Spark;

public class Factory {

    private static final Logger log = LoggerFactory.getLogger( Factory.class );

    public static ProxyServer createServer(int serverPort, int tcpPort) {
        DirectForward directForward = new DirectForward();

        RemoteForward remoteForward = new RemoteForward( tcpPort, 10000 );
        remoteForward.getAccess().put( "test1", new RemoteAccess( "test1", "client1" ) );
        remoteForward.getAccess().put( "test2", new RemoteAccess( "test2", "client2" ) );

        DirectSite spark = new DirectSite("/spark", "localhost:9999");
        directForward.sites.put( spark.getPath(),spark );

        LocalSite localSite = new LocalSite() {
            @Override
            protected Optional<ContentResult> onHandlePath(String verb, String path, String body) throws HttpException {
                if(verb.equals( "GET" )) {
                   if(path.equals( "/local" )) {
                       return Optional.of(  new ContentResult("hello"));
                   }
                }
                return Optional.empty();
            }
        };

        return new ProxyServer(
                serverPort,
                directForward,
                remoteForward,
                localSite
        );
    }

    public static void startSpark(int port) throws Exception {
        Spark.port(port);
        Spark.get("/hello", "text/plain", (req, res) -> {
            log.info(req.headers().toString());
            log.info(req.contentType());
            log.info(req.body());
            return "Hello";
        });

        Spark.post("/hello", "application/json", (req, res) -> {
            System.out.println(req.body());
            return "Hello";
        });

        Spark.post("/echo", "text/plain", (req, res) -> req.body());
        Spark.put("/echo", "text/plain", (req, res) -> req.body());
        RawHttp.waitForPortToBeTaken( port, Duration.ofSeconds( 2 ) );
    }

    public static void stopSpark() {
        Spark.stop();
    }

    static int getRest(int port, String path) {
        try (TcpRawHttpClient client = new TcpRawHttpClient()) {
            RawHttpRequest request = new RawHttp().parseRequest( String.format( "GET localhost:%d%s HTTP/1.1", port, path ) );
            EagerHttpResponse<?> rawResponse = client.send(request).eagerly();
           return rawResponse.getStatusCode();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static int getRestText(int port, String path, String body) {
        try (TcpRawHttpClient client = new TcpRawHttpClient()) {
            String req = String.format( "GET localhost:%d%s HTTP/1.1\r\nUser-Agent: RawHTTP", port, path );
            if(body!=null) {
                req = req + "\r\n"
                        + "Content-Length: " + body.length() + "\r\n"
                        + "Content-Type: text/plain\r\n"
                        + "Accept: text/plain\r\n"
                        + "\r\n"
                        + body;

            }
            EagerHttpResponse<?> rawResponse = client.send(new RawHttp().parseRequest( req )).eagerly();
            return rawResponse.getStatusCode();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
