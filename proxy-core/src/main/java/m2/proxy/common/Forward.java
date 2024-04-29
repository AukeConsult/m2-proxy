package m2.proxy.common;

import m2.proxy.server.ProxyServer;

public interface Forward {

    ProxyServer getServer();
    void setServer(ProxyServer server);

}
