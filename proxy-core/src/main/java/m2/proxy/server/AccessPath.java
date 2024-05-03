package m2.proxy.server;

import m2.proxy.tcp.handlers.SessionHandler;

public class AccessPath {
    private final String accessPath;
    private final String clientId;
    private final SessionHandler sessionHandler;

    public String getAccessPath() { return accessPath; }
    public String getClientId() { return clientId; }
    public SessionHandler getSessionHandler() { return sessionHandler; }

    public AccessPath(String accessPath, String clientId, SessionHandler sessionHandler) {
        this.accessPath = accessPath;
        this.clientId = clientId;
        this.sessionHandler = sessionHandler;
    }
}