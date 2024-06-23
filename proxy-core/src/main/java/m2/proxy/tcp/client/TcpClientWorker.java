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

import java.net.ConnectException;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings( { "UnusedReturnValue", "NullableProblems" } )
public class TcpClientWorker extends ConnectionWorker {
    private static final Logger log = LoggerFactory.getLogger( TcpClientWorker.class );

    private final TcpClient tcpClient;
    private final String connectAddress;
    private final int connectPort;
    private final AtomicReference<ConnectionHandler> connectionHandler = new AtomicReference<>();

    private final EventLoopGroup bossGroup;

    private final String workerId;
    public String getWorkerId() { return workerId; }

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

    public TcpClientWorker(final TcpClient tcpClient, String connectAddress, int connectPort) {
        this.tcpClient = tcpClient;
        this.workerId = connectAddress+connectPort;
        this.connectAddress = connectAddress;
        this.connectPort = connectPort;
        this.bossGroup = new NioEventLoopGroup(2);
    }

    @Override
    public void disconnect(boolean notifyRemote) {

        log.trace( "{} -> disconnect, notify: {}", tcpClient.myId(), notifyRemote );
        if (!stopping.getAndSet( true )) {
            if (connectionHandler.get() != null) {
                if (notifyRemote) {
                    connectionHandler.get().disconnectRemote();
                } else {
                    connectionHandler.get().disconnect();
                }
            } else {
                log.warn( "{} -> connections handler missing, notify: {}", tcpClient.myId(), notifyRemote );
            }
            bossGroup.shutdownGracefully();
            // remove connection handler
            connected.set(false);
            while(running.get()) {
                try {
                    Thread.sleep( 10 );
                } catch (InterruptedException ignored) {
                }
            }
            log.info( "{} -> disconnected", tcpClient.myId() );
        }
    }

    @Override
    public void run() {

        if (!running.getAndSet( true )) {

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

                log.debug( "{} -> Connecting server {}:{}", tcpClient.myId(), connectAddress, connectPort );
                ChannelFuture f = bootstrap.connect( connectAddress, connectPort ).sync();
                f.channel().closeFuture().sync();

            } catch (InterruptedException e) {
                log.debug( "{} -> Connect server {}:{} -> {}", tcpClient.myId(), connectAddress, connectPort, e.getMessage() );
                tcpClient.serviceDisconnected( getHandler(), "Interupted" );
            } catch (Exception e) {
                if (e.getCause() instanceof ConnectException) {
                    log.debug( "{} -> Connect server {}:{} not avail -> {}", tcpClient.myId(), connectAddress, connectPort , e.getMessage() );
                    tcpClient.serviceDisconnected( getHandler(), "Closed" );
                } else {
                    log.debug( "{} -> Connect server {}:{} -> {}", tcpClient.myId(), connectAddress, connectPort , e.getMessage() );
                    tcpClient.serviceDisconnected( getHandler(), "Exception" );
                }
            } finally {
                running.set(false);
                stopping.set(false);
            }
        }
    }
}
