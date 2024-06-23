package m2.proxy.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import m2.proxy.common.HttpHelper;
import m2.proxy.server.ProxyServer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rawhttp.core.EagerHttpResponse;
import rawhttp.core.RawHttp;
import spark.Spark;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class RunServicesTest {
    private static final Logger log = LoggerFactory.getLogger( RunServicesTest.class );
    private final HttpHelper httpHelper = new HttpHelper();

    public static void sparkStart(int port) throws Exception {
        Spark.port( port );
        Spark.get( "/hello", "text/plain", (req, res) -> {
            if (!req.body().isEmpty()) {
                log.info( req.body() );
                return req.body();
            } else {
                return "hello";
            }
        } );

        Spark.get( "/json", "application/json", (req, res) -> {
            log.info( req.body() );
            return req.body();
        } );

        Spark.post( "/echo", "text/plain", (req, res) -> req.body() );
        Spark.put( "/echo", "text/plain", (req, res) -> req.body() );
        RawHttp.waitForPortToBeTaken( port, Duration.ofSeconds( 2 ) );
    }

    public static void sparkStop() {
        Spark.stop();
    }

    @Test
    void server_local_test() {

        ProxyServer server = Factory.createServer( 9000, 9001 );
        server.start();
        assertTrue( server.isRunning() );
        Optional<EagerHttpResponse<?>> resp = Factory.getRest( 9000, "/local/hello", "hello" );
        assertTrue( resp.isPresent() );
        assertEquals( 200, resp.get().getStatusCode() );

        resp = Factory.getRest( 9000, "/localX/hello" );
        assertTrue( resp.isPresent() );
        assertEquals( 404, resp.get().getStatusCode() );

        resp = Factory.getRest( 9000, "/local/hello", "hello" );
        assertTrue( resp.isPresent() );
        assertEquals( 200, resp.get().getStatusCode() );
        assertEquals( "hello", resp.get().getBody().map( httpHelper.decodeBody() ).orElse( "" ) );

        resp = Factory.getRest( 9000, "/local/hello" );
        assertTrue( resp.isPresent() );
        assertEquals( 200, resp.get().getStatusCode() );

        resp = Factory.getRest( 9000, "/local/json", "hello" );
        assertTrue( resp.isPresent() );
        assertEquals( 200, resp.get().getStatusCode() );
        assertEquals( "{\"data\":\"hello\"}", resp.get().getBody().map( httpHelper.decodeBody() ).orElse( "" ) );

        JsonObject bodyObj = new JsonObject();
        bodyObj.addProperty( "test", "teststring" );
        resp = Factory.getRest( 9000, "/local/json", bodyObj );
        assertTrue( resp.isPresent() );
        assertEquals( 200, resp.get().getStatusCode() );
        assertEquals( "{\"test\":\"teststring\"}", resp.get().getBody().map( httpHelper.decodeBody() ).orElse( "" ) );

        JsonObject bodyObj2 = new JsonObject();
        bodyObj2.addProperty( "test", "teststring" );
        JsonArray array = new JsonArray();
        for (int i = 0; i < 5; i++) {
            JsonObject row = new JsonObject();
            row.addProperty( "test", "sdfsdfsdf" );
            array.add( row );
        }
        bodyObj2.add( "list", array );

        resp = Factory.getRest( 9000, "/local/json", bodyObj2 );
        assertTrue( resp.isPresent() );
        assertEquals( 200, resp.get().getStatusCode() );
        assertEquals( "{\"test\":\"teststring\",\"list\":[{\"test\":\"sdfsdfsdf\"},{\"test\":\"sdfsdfsdf\"},{\"test\":\"sdfsdfsdf\"},{\"test\":\"sdfsdfsdf\"},{\"test\":\"sdfsdfsdf\"}]}",
                resp.get().getBody().map( httpHelper.decodeBody() ).orElse( "" ) );

        server.stop();
        assertFalse( server.isRunning() );
    }

    @Test
    void send_direct_test() throws Exception {

        sparkStart( 9999 );

        ProxyServer server = Factory.createServer( 9000, 9001 );
        server.start();
        assertTrue( server.isRunning() );
        Optional<EagerHttpResponse<?>> resp = Factory.getRest( 9000, "/spark/hello" );
        assertTrue( resp.isPresent() );
        assertEquals( 200, resp.get().getStatusCode() );
        assertEquals( "hello", resp.get().getBody().map( httpHelper.decodeBody() ).orElse( "" ) );

        resp = Factory.getRest( 9000, "/local/jsonX" );
        assertTrue( resp.isPresent() );
        assertEquals( 404, resp.get().getStatusCode() );
        assertEquals( "NOTFOUND", resp.get().getStartLine().getReason() );

        resp = Factory.putRest( 9000, "how are you" );
        assertTrue( resp.isPresent() );
        assertEquals( 200, resp.get().getStatusCode() );
        assertEquals( "how are you", resp.get().getBody().map( httpHelper.decodeBody() ).orElse( "" ) );

        JsonObject bodyObj = new JsonObject();
        bodyObj.addProperty( "test", "hello" );
        resp = Factory.getRest( 9000, "/spark/json", bodyObj );
        assertTrue( resp.isPresent() );
        assertEquals( 200, resp.get().getStatusCode() );
        assertEquals( "{\"test\":\"hello\"}", resp.get().getBody().map( httpHelper.decodeBody() ).orElse( "" ) );

        JsonObject bodyObj2 = new JsonObject();
        bodyObj2.addProperty( "test", "teststring" );
        JsonArray array = new JsonArray();
        for (int i = 0; i < 5; i++) {
            JsonObject row = new JsonObject();
            row.addProperty( "test", "sdfsdfsdf" );
            array.add( row );
        }
        bodyObj2.add( "list", array );

        resp = Factory.getRest( 9000, "/local/json", bodyObj2 );
        assertTrue( resp.isPresent() );
        assertEquals( 200, resp.get().getStatusCode() );
        assertEquals( "{\"test\":\"teststring\",\"list\":[{\"test\":\"sdfsdfsdf\"},{\"test\":\"sdfsdfsdf\"},{\"test\":\"sdfsdfsdf\"},{\"test\":\"sdfsdfsdf\"},{\"test\":\"sdfsdfsdf\"}]}",
                resp.get().getBody().map( httpHelper.decodeBody() ).orElse( "" ) );

        server.stop();
        assertFalse( server.isRunning() );
        sparkStop();
    }
}
