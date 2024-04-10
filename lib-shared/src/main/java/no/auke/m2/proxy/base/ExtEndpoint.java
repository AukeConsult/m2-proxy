package no.auke.m2.proxy.base;

import no.auke.m2.proxy.types.TypeProtocol;

public class ExtEndpoint {

    public String endpointId;
    public String url;
    public String host;
    public int port;
    public TypeProtocol typeProtocol;

    public ExtEndpoint() {}
    public ExtEndpoint setWithUrl(TypeProtocol typeProtocol, String endpointId, String url) {
        this.endpointId=endpointId;
        this.typeProtocol=typeProtocol;
        this.url=url;
        return this;
    }
    public ExtEndpoint setWithHost(TypeProtocol typeProtocol, String endpointId, String host, int port) {
        this.endpointId=endpointId;
        this.typeProtocol=typeProtocol;
        this.host=host;
        this.port=port;
        return this;
    }

    @Override
    public String toString() {
        return host+":"+port;
    }
}