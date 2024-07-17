package m2.proxy.server.tcp;

import m2.proxy.tcp.handlers.SessionHandler;

public class ClientSession {
    private final String accessKey;
    private final String clientId;
    private final SessionHandler sessionHandler;

    public String getAccessKey() { return accessKey; }
    public String getClientId() { return clientId; }
    public SessionHandler getSessionHandler() { return sessionHandler; }

    public ClientSession(String accessKey, String clientId, SessionHandler sessionHandler) {
        this.accessKey = accessKey;
        this.clientId = clientId;
        this.sessionHandler = sessionHandler;
    }
}