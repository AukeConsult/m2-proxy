package no.auke.m2.proxy;

import no.auke.m2.proxy.executors.ServiceBaseExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ProxyServer extends ServiceBaseExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProxyServer.class);


    BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
    ExecutorService executor = new ThreadPoolExecutor(10,20,2,TimeUnit.SECONDS,tasks);

    private final String serverId;
    private final String host;
    private final int port;

    public String getServerId() {return serverId;}
    public String getHost() {return host;}
    public int getPort() {return port;}

    public List<ClientSession> getClientSessions() {
        return new ArrayList<>(clientSessions.values());
    }

    private Map<String,ClientSession> clientSessions = new HashMap<>();

    private ServerSocket serverSocketWait=null;

    private AtomicInteger requests = new AtomicInteger();
    public int getRequests() {
        return requests.getAndSet(0);
    }

    public ProxyServer(String serverId, String host, int port) {
        this.serverId =serverId;
        this.host=host;
        this.port=port;
    }

    @Override
    protected boolean open() {
        log.info("{} -> server open, http://{}:{}", serverId,host,port);
        return true;
    }

    @Override
    protected void startServices() {}

    @Override
    protected void execute() {

        if(isRunning()) {

            try (ServerSocket serverSocket = new ServerSocket(port)) {

                serverSocketWait=serverSocket;
                log.debug("{} -> running",serverId);

                while(isRunning() && !serverSocket.isClosed()) {
                    try {
                        Socket clientAccept = serverSocket.accept();
                        requests.incrementAndGet();
                        if(isRunning()) {
                            log.info("{} -> accept {}:{}", serverId,clientAccept.getInetAddress().getHostName(),clientAccept.getPort());
                            // verify clients
                            String clientId = clientAccept.getInetAddress().getHostName();
                            if(!clientSessions.containsKey(clientId)) {
                                ClientSession session = new ClientSession(this,clientId,clientAccept.getInetAddress(),"localhost",3000);
                                clientSessions.put(clientId,session);
                            }
                            clientSessions.get(clientId).executeRequest(clientAccept);
                        }
                    } catch (SocketException e) {
                        log.info("{} -> socket error: {}",serverId,e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("{} -> exception: {}",serverId,e.getMessage());
            }
        }
    }

    @Override
    protected void forceClose() {
        log.trace("{} -> force close",serverId);
        if(serverSocketWait!=null) {
            try {
                serverSocketWait.close();
            } catch (IOException e) {
                log.error("{} -> error close: {}",serverId,e.getMessage());
            }
        }
    }

    @Override
    protected void close() {
        log.info("{} -> Stopped",serverId);
    }

}