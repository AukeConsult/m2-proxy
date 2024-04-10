package no.auke.m2.proxy.server.base;

import no.auke.m2.proxy.executors.ServiceBaseExecutor;
import no.auke.m2.proxy.server.access.AccessController;
import no.auke.m2.proxy.types.TypeServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class ProxyServer extends ServiceBaseExecutor {

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

    private final ProxyMain mainService;
    private final String serverId;
    private final String bootAddress;
    private final int port;
    private final int inActiveTimeSeconds;
    private final TypeServer typeServer;

    public AccessController getAccesController() {
        return mainService.getAccesController();
    }

    public String getServerId() {return serverId;}
    private String hostAddress;
    public String getHostAddress() {return hostAddress;}
    public int getPort() {return port;}

    public int getActiveSessions() {
        return clientSessions.size();
    }
    public List<Session> getClientSessions() {
        return new ArrayList<>(clientSessions.values());
    }
    protected final Map<Long, Session> clientSessions = new ConcurrentHashMap<>();

    private ServerSocket serverSocketClose =null;
    private final AtomicInteger requests = new AtomicInteger();
    public int getRequests() {
        return requests.getAndSet(0);
    }

    public void cleanSessions() {
        // clean sessions no longer active
        getClientSessions().forEach(s -> {
            if(System.currentTimeMillis() - s.getLastActive()>(inActiveTimeSeconds*1000)) {
                clientSessions.remove(s.getAccess().getAccessId());
            }
        });
    }

    protected void executeSession(Session session, Socket clientSocket, long requestId, BufferedReader inputStream, StringBuilder header) {
        if(!clientSessions.get(session.getAccess().getAccessId()).execute(clientSocket, requestId, inputStream, header)) {
            // failed
            clientSessions.remove(session.getAccess().getAccessId());
        }
    }

    protected abstract void executeRequest(ProxyServer proxyServer, Socket clientSocket, long requestId);
    public abstract Object readInput(BufferedReader inputStream) throws IOException;

    public ProxyServer(ProxyMain mainService,
                       String serverId,
                       String bootAddress,
                       int port,
                       int inActiveTimeSeconds,
                       int corePooSize,
                       int maximumPoolSize,
                       int keepAliveTime,
                       TypeServer typeServer
    ) {
        this.mainService=mainService;
        this.serverId =serverId;
        this.bootAddress=bootAddress;
        this.port=port;
        this.inActiveTimeSeconds=inActiveTimeSeconds;
        this.typeServer=typeServer;

        executor = new ThreadPoolExecutor(corePooSize,maximumPoolSize,keepAliveTime,TimeUnit.SECONDS,tasks);
        rnd.setSeed(System.currentTimeMillis());
    }

    @Override
    protected final boolean open() {
        log.info("{} -> Proxy server open, http://{}:{}", serverId,hostAddress,port);
        return true;
    }

    @Override
    protected final void execute() {

        if(isRunning()) {

            try (ServerSocket serverSocket = new ServerSocket(port)) {

                // set the current socket for closing
                hostAddress = serverSocket.getLocalSocketAddress().toString();

                serverSocketClose = serverSocket;
                log.debug("{} -> running",serverId);

                // waiting for incoming requests
                while(isRunning() && !serverSocket.isClosed()) {
                    try {
                        final Socket clientAccept = serverSocket.accept();
                        requests.incrementAndGet();
                        if(isRunning()) {

                            long requestId = rnd.nextLong(ProxyMain.MAX_ID);
                            log.info("{} -> RequestId: {}, from IP:PORT {}:{}",
                                    serverId,
                                    requestId,
                                    clientAccept.getInetAddress().getHostName(),
                                    clientAccept.getPort()
                            );

                            // execute the request
                            // all magic happen in there
                            executeRequest(this,clientAccept, requestId);

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
    protected final void forceClose() {
        log.trace("{} -> force close",serverId);
        if(serverSocketClose !=null) {
            try {
                serverSocketClose.close();
            } catch (IOException e) {
                log.error("{} -> error close: {}",serverId,e.getMessage());
            }
        }
    }
    @Override
    protected final void close() {
        log.info("{} -> Stopped",serverId);
    }
    @Override
    protected final void startServices() {}


}