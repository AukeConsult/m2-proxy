package m2.proxy;

import m2.proxy.types.TransportProtocol;

public class EndpointPath {

    public final String path;
    public final TransportProtocol transportProtocol;
    public String host;
    public int port;

    public EndpointPath(String path, TransportProtocol transportProtocol) {
        this.path=path;
        this.transportProtocol=transportProtocol;
    }
    public EndpointPath setHost(String host, int port) {
        this.host=host;
        this.port=port;
        return this;
    }

    @Override
    public String toString() {
        return transportProtocol.toString()+"://"+host+":"+port+"/"+path;
    }
}