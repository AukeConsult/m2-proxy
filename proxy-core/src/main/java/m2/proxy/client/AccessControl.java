package m2.proxy.client;

import m2.proxy.common.Service;

import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class AccessControl implements Service {
    Service service;
    @Override
    public Service getService() {
        return service;
    }
    @Override public void setService(Service service) {
        this.service=service;
    }
}
