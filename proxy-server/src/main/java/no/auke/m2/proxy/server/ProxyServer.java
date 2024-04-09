package no.auke.m2.proxy.server;

import no.auke.m2.proxy.executors.ServiceBaseExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ProxyServer extends ServiceBaseExecutor {
    private static final Logger log = LoggerFactory.getLogger(ProxyServer.class);

    private final Random rnd = new Random();
    private final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
    public int getWaitingTasks() {
        return tasks.size();
    }

    private final ExecutorService executor;
    public ExecutorService getRequestExecutor() {
        return executor;
    }

    private final String serverId;
    private final String host;
    private final int port;
    private final int inActiveTimeMs=100000;

    public String getServerId() {return serverId;}
    public String getHost() {return host;}
    public int getPort() {return port;}

    public int getActiveSessions() {
        return clientSessions.size();
    }

    public List<ClientSession> getClientSessions() {
        return new ArrayList<>(clientSessions.values());
    }

    private final Map<String,ClientSession> clientSessions = new HashMap<>();
    private ServerSocket serverSocketWait=null;
    private final AtomicInteger requests = new AtomicInteger();
    public int getRequests() {
        return requests.getAndSet(0);
    }

    public void cleanSessions() {
        // clean sessions no longer active
        getClientSessions().forEach(s -> {
            if(System.currentTimeMillis() - s.getLastActive()>inActiveTimeMs) {
                clientSessions.remove(s.getClientId());
            }
        });

    }
    public ProxyServer(String serverId, String host, int port) {
        this.serverId =serverId;
        this.host=host;
        this.port=port;
        executor = new ThreadPoolExecutor(10,20,2,TimeUnit.SECONDS,tasks);
        rnd.setSeed(System.currentTimeMillis());
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
                            long requestId = rnd.nextLong(Long.MAX_VALUE);
                            // verify clients
                            String clientId = clientAccept.getInetAddress().getHostName();

                            log.info("{} -> {}, RequestId: {}, from IP:PORT {}:{}", serverId, clientId, requestId, clientAccept.getInetAddress().getHostName(),clientAccept.getPort());

                            if(!clientSessions.containsKey(clientId)) {

                                ClientSession session = new ClientSession(this,clientId,clientAccept.getInetAddress(),"localhost",3000);
                                if(session.checkClient()) {
                                    clientSessions.put(clientId,session);
                                    log.info("{} -> {}, RequestId: {}, Open session, to external server: {}:{}",
                                            getServerId(),
                                            clientId,
                                            requestId,
                                            "localhost",
                                            3000
                                    );
                                }
                            }

                            if(clientSessions.containsKey(clientId)) {
                                clientSessions.get(clientId).executeRequest(clientAccept, requestId);
                            }

                        }
                    } catch (SocketException e) {
                        log.info("{} -> {}",serverId,e.getMessage());
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