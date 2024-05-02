package m2.proxy.tcp.server;

public class AccessPath {
    private final String accessPath;
    private final String clientId;
    public String getAccessPath() { return accessPath; }
    public String getClientId() { return clientId; }
    public AccessPath(String accessPath, String clientId) {
        this.accessPath = accessPath;
        this.clientId = clientId;
    }
}