package m2.proxy.common;

import m2.proxy.server.remote.RemoteSite;
import org.junit.jupiter.api.Test;
import rawhttp.core.RawHttp;
import rawhttp.core.RawHttpRequest;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class HttpHelperTest {

    RawHttp rawHttp = new RawHttp();
    HttpHelper httpHelper = new HttpHelper();

    @Test
    void directforward_request () {
        RemoteSite site = new RemoteSite( "/test", "localhost:9999" );
        RawHttpRequest request = rawHttp.parseRequest("GET localhost:8000/test/hello HTTP/1.1");
        Optional<RawHttpRequest> requestOUT = httpHelper.pathDirectSite(site,request);
        assertTrue(requestOUT.isPresent());
        assertEquals("GET /hello HTTP/1.1",requestOUT.get().getStartLine().toString());
    }

    @Test
    void directforward_request_empty () {
        RemoteSite site = new RemoteSite( "/test", "localhost:9999" );
        RawHttpRequest request = rawHttp.parseRequest("GET localhost:8000/xxxx/hello HTTP/1.1");
        Optional<RawHttpRequest> requestOUT = httpHelper.pathDirectSite(site,request);
        assertFalse(requestOUT.isPresent());
    }

    @Test
    void directforward_request_query () {
        RemoteSite site = new RemoteSite( "/test", "localhost:9999" );
        RawHttpRequest request = rawHttp.parseRequest("GET localhost:8000/test/hello?id=1 HTTP/1.1");
        Optional<RawHttpRequest> requestOUT = httpHelper.pathDirectSite(site,request);
        assertTrue(requestOUT.isPresent());
        assertEquals("GET /hello?id=1 HTTP/1.1",requestOUT.get().getStartLine().toString());
        assertEquals("http://localhost:9999/hello?id=1",requestOUT.get().getStartLine().getUri().toString());
    }

    @Test
    void access_request () {

        RawHttpRequest request = rawHttp.parseRequest("GET localhost:8000/12345/hello HTTP/1.1");
        assertTrue(httpHelper.getAccessPath( request ).isPresent());
        assertEquals("12345",httpHelper.getAccessPath( request ).get());
        Optional<RawHttpRequest> requestOUT = httpHelper.pathAccessKey("12345",request);
        assertTrue(requestOUT.isPresent());
        assertEquals("GET /hello HTTP/1.1",requestOUT.get().getStartLine().toString());
    }

    @Test
    void access_request_query ()  {

        RawHttpRequest request = rawHttp.parseRequest("GET localhost:8000/12345/hello?id=1 HTTP/1.1");
        assertTrue(httpHelper.getAccessPath( request ).isPresent());
        assertEquals("12345",httpHelper.getAccessPath( request ).get());
        Optional<RawHttpRequest> requestOUT = httpHelper.pathAccessKey("12345",request);
        assertTrue(requestOUT.isPresent());
        assertEquals("GET /hello?id=1 HTTP/1.1",requestOUT.get().getStartLine().toString());
    }

    @Test
    void access_request_no () {

        RawHttpRequest request = rawHttp.parseRequest("GET localhost:8000/12345/hello HTTP/1.1");
        assertTrue(httpHelper.getAccessPath( request ).isPresent());
        assertEquals("12345",httpHelper.getAccessPath( request ).get());
        Optional<RawHttpRequest> requestOUT = httpHelper.pathAccessKey("/11111",request);
        assertFalse(requestOUT.isPresent());
    }

}
