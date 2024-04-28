package m2.proxy.server;

public class RemoteAccess {
    private final String key;
    private final String clientId;
    public String getKey() { return key; }
    public String getClientId() { return clientId; }
    public RemoteAccess(String key, String clientId) {
        this.key = key;
        this.clientId = clientId;
    }
}