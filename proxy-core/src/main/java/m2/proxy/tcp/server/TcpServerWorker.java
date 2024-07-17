package m2.proxy.tcp.server;

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import m2.proxy.proto.MessageOuterClass.Message;
import m2.proxy.tcp.handlers.ConnectionHandler;
import m2.proxy.tcp.handlers.ConnectionWorker;
import m2.proxy.tcp.handlers.MessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

class TcpServerWorker extends ConnectionWorker {

    private static final Logger log = LoggerFactory.getLogger( TcpServer.class );

    private final TcpServer tcpServer;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final String connectAddress;
    private final int ConnectPort;

    public TcpServerWorker(final TcpServer tcpServer) {
        this.tcpServer = tcpServer;
        this.connectAddress = tcpServer.connectAddress();
        this.ConnectPort = tcpServer.connectPort();
        this.bossGroup = new NioEventLoopGroup( tcpServer.nettyConnectThreads() );
        this.workerGroup = new NioEventLoopGroup( tcpServer.nettyWorkerThreads() );
    }

    @Override
    public void run() {

        if (!running.getAndSet( true )) {
            log.info( "{} -> Started Connect worker", tcpServer.myId() );
            try {

                ServerBootstrap serverBootstrap = new ServerBootstrap();
                serverBootstrap.group( bossGroup, workerGroup )
                        .channel( NioServerSocketChannel.class )
                        .option( ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT )
                        .option( ChannelOption.SO_BACKLOG, 100 )
                        .childOption( ChannelOption.SO_KEEPALIVE, true )
                        .childHandler( new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ch.pipeline().addFirst( new MessageDecoder( tcpServer ) );
                                ch.pipeline().addLast( new SimpleChannelInboundHandler<byte[]>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, byte[] bytes) {
                                        try {
                                            Message msg = Message.parseFrom( bytes );
                                            String channelId = ctx.channel().id().asLongText();
                                            if (tcpServer.getClientHandles().containsKey( channelId )) {
                                                tcpServer.getClientHandles().get( channelId ).readMessage( msg );
                                            } else {
                                                log.warn( "{} -> client not open, ch: {}, addr: {}",
                                                        tcpServer.myId(),
                                                        channelId, ctx.channel().remoteAddress().toString() );
                                            }
                                        } catch (InvalidProtocolBufferException e) {
                                            log.error( "error create message from bytes" );
                                        }
                                    }
                                    @Override
                                    public void channelActive(ChannelHandlerContext ctx) {
                                        String channelId = ctx.channel().id().asLongText();
                                        if (tcpServer.getClientHandles().containsKey( channelId )) {
                                            log.error( "{} -> CHANNEL EXISTS, ch: {}, addr: {}",
                                                    tcpServer.myId(),
                                                    channelId, ctx.channel().remoteAddress().toString() );
                                        } else {
                                            final ConnectionHandler handler = tcpServer.setConnectionHandler();
                                            handler.setTcpBase( tcpServer );
                                            handler.initServer( channelId, ctx );
                                            tcpServer.getClientHandles().put( handler.getChannelId(), handler );
                                        }
                                    }
                                } );
                            }
                        } );

                ChannelFuture f = serverBootstrap.bind( connectAddress, ConnectPort ).sync();
                f.channel().closeFuture().sync();
                f.channel().close();

            } catch (InterruptedException e) {
                log.info( "{} -> Stopp interupt: {}", tcpServer.myId(), e.getMessage() );
            } catch (Exception e) {
                log.info( "{} -> Stopp error: {}", tcpServer.myId(), e.getMessage() );
            } finally {
                log.info( "{} -> Stopped Connect worker", tcpServer.myId() );
                running.set( false );
                stopping.set( false );
            }
        }
    }
    @Override public void disconnectRemote(boolean notifyRemote) {
        if (running.get() && !stopping.getAndSet( true )) {
            new ArrayList<>( tcpServer.getClientHandles().values() )
                    .forEach( tcpServer::disconnectRemote );
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
