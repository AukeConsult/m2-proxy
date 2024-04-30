package m2.proxy.server;

public class RemoteAccess {
    private final String accessPath;
    private final String clientId;
    public String getAccessPath() { return accessPath; }
    public String getClientId() { return clientId; }
    public RemoteAccess(String accessPath, String clientId) {
        this.accessPath = accessPath;
        this.clientId = clientId;
    }
}