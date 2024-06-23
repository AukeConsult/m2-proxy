package m2.proxy.tcp;

import m2.proxy.tcp.handlers.ConnectionHandler;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TcpBaseTest {

    public TcpBase getTcpBase() {
        return new TcpBase() {
            @Override protected boolean onCheckAccess(String accessPath, String clientAddress, String accessToken, String agent) { return false; }
            @Override protected Optional<String> onSetAccess(String userId, String passWord, String clientAddress, String accessToken, String agent) {
                return Optional.of("12345");
            }
            @Override public ConnectionHandler setConnectionHandler() { return null; }
            @Override public void connect(ConnectionHandler handler) { }
            @Override public void disconnect(ConnectionHandler handler) { }
            @Override public void onDisconnected(ConnectionHandler handler) { }
            @Override public void onStart() { }
            @Override public void onStop() { }
            @Override protected void execute() { }
        };
    }

    @Test
    void setAccessTest() {
        TcpBase t = getTcpBase();
        Optional<String> accessKey = t.setAccess( "","","","", "" );
        assertEquals("12345",accessKey.get());
        assertTrue(t.getAccessCacheList().size()>0);
    }
}
