package m2.proxy.tcp.client;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import m2.proxy.proto.MessageOuterClass.Message;
import m2.proxy.tcp.handlers.ConnectionHandler;
import m2.proxy.tcp.handlers.ConnectionWorker;
import m2.proxy.tcp.handlers.MessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings( { "UnusedReturnValue", "NullableProblems" } )
public class TcpClientWorker extends ConnectionWorker {
    private static final Logger log = LoggerFactory.getLogger( TcpClientWorker.class );


    private final TcpClient tcpClient;
    private final String workerId;
    private final String myAddress;
    private final int myPort;

    private final AtomicReference<ConnectionHandler> connectionHandler = new AtomicReference<>();

    public TcpClient getTcpClient() { return tcpClient; }
    public String getWorkerId() { return workerId; }
    public String getMyAddress() { return myAddress; }
    public int getMyPort() { return myPort; }

    private EventLoopGroup bossGroup;

    public ConnectionHandler getHandler() {
        if (connectionHandler.get() == null) {
            connectionHandler.set( tcpClient.setConnectionHandler() );
            connectionHandler.get().setTcpService( tcpClient );
            connectionHandler.get().setConnectionWorker( this );
        }
        return connectionHandler.get();
    }

    // sending to server
    public boolean sendMessage(String message) { return getHandler().sendMessage( message ); }
    public boolean sendRawMessage(byte[] bytes) { return getHandler().sendRawMessage( bytes ); }

    private void createBossGroup() {
        this.bossGroup = new NioEventLoopGroup(2);
    }
    public TcpClientWorker(final String workerId, final TcpClient tcpClient, String myAddress, int myPort) {
        this.tcpClient = tcpClient;
        this.workerId = workerId;
        this.myAddress = myAddress;
        this.myPort = myPort;
        createBossGroup();
    }

    @Override
    public void disconnect(boolean notifyRemote) {
        if (running.getAndSet( false )) {

            if (connectionHandler.get() != null) {
                log.debug( "{} -> tcp client stopped: {}", tcpClient.myId(), connectionHandler.get().getRemoteClientId() );
                if (notifyRemote) {
                    connectionHandler.get().disconnectRemote();
                } else {
                    connectionHandler.get().disconnect();
                }
            }
            if(!bossGroup.isTerminated()) {
                bossGroup.shutdownGracefully();
            }
            createBossGroup();
            // remove connection handler
            connectionHandler.set(null);
            connected.set(false);
        }
    }

    @Override
    public void run() {

        if (!running.getAndSet( true )) {
            log.debug( "{} -> Server thread start", tcpClient.myId() );
            try {

                Bootstrap bootstrap = new Bootstrap();
                bootstrap.group( bossGroup )
                        .channel( NioSocketChannel.class )
                        .handler( new ChannelInitializer<SocketChannel>() {
                            @Override protected void initChannel(SocketChannel ch) {
                                ch.pipeline().addFirst( new MessageDecoder( tcpClient ) );
                                ch.pipeline().addLast( new SimpleChannelInboundHandler<byte[]>() {
                                    @Override protected void channelRead0(ChannelHandlerContext ctx, byte[] bytes) {
                                        try {
                                            Message msg = Message.parseFrom( bytes );
                                            getHandler().readMessage( msg );
                                        } catch (InvalidProtocolBufferException e) {
                                            log.error( "error create message from bytes" );
                                        }
                                        Thread.yield();
                                    }

                                    @Override public void channelActive(ChannelHandlerContext ctx) {
                                        getHandler().initClient( ctx.channel().id().asLongText(), ctx );
                                    }
                                } );
                            }
                        } );

                ChannelFuture f = bootstrap.connect( myAddress, myPort ).sync();
                f.channel().closeFuture().sync();

            } catch (InterruptedException e) {
                log.warn( "{} -> Connect server Interrupt error: {}", tcpClient.myId(), e.getMessage() );
                connectionErrors.incrementAndGet();
                tcpClient.onDisconnected( getHandler() );
            } catch (Exception e) {
                if (e.getCause() != null) {
                    log.warn( "{} -> Connect server error: {}", tcpClient.myId(), e.getCause().getMessage() );
                } else {
                    log.warn( "{} -> Connect server error: {}", tcpClient.myId(), e.getMessage() );
                }
                connectionErrors.incrementAndGet();
                tcpClient.onDisconnected( getHandler() );
            }
            log.debug( "{} -> Server thread stopped", tcpClient.myId() );

        }
    }
}
