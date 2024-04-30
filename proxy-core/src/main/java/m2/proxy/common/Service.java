package m2.proxy.common;

import m2.proxy.server.ProxyServer;

public interface Service {

    Service getService();
    void setService(Service service);

}
