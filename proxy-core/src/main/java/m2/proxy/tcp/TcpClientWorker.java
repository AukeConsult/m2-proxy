package m2.proxy.tcp;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import m2.proxy.proto.MessageOuterClass;
import m2.proxy.tcp.handlers.ConnectionHandler;
import m2.proxy.tcp.handlers.ConnectionWorker;
import m2.proxy.tcp.handlers.MessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

public class TcpClientWorker extends ConnectionWorker {
    private static final Logger log = LoggerFactory.getLogger( TcpClientWorker.class );

    private final TcpBaseClientBase server;
    private final EventLoopGroup bossGroup;
    private final String serverAddr;
    private final int serverPort;
    private final AtomicReference<ConnectionHandler> connectionHandler = new AtomicReference<>();

    public ConnectionHandler getHandler() {
        if (connectionHandler.get() == null) {
            connectionHandler.set( server.setConnectionHandler() );
            connectionHandler.get().setServer( server );
            connectionHandler.get().setConnectionWorker( this );
        }
        return connectionHandler.get();
    }

    public TcpClientWorker(final TcpBaseClientBase server, String serverAddr, int serverPort) {
        this.server = server;
        this.serverAddr = serverAddr;
        this.serverPort = serverPort;
        this.bossGroup = new NioEventLoopGroup();
    }

    @Override
    public void stopWorker() {
        if (running.getAndSet( false )) {
            log.debug( "{} -> Client disconnect", server.getClientId() );
            bossGroup.shutdownGracefully();
            if (connectionHandler.get() != null) {
                connectionHandler.get().close();
            }
        }
    }

    @Override
    public void run() {
        log.debug( "{} -> Client thread started", server.getClientId() );
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group( bossGroup )
                    .channel( NioSocketChannel.class )
                    .handler( new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addFirst( new MessageDecoder( server ) );
                            ch.pipeline().addLast( new SimpleChannelInboundHandler<MessageOuterClass.Message>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, MessageOuterClass.Message msg) {
                                    getHandler().prosessMessage( msg );
                                }
                                @Override
                                public void channelActive(ChannelHandlerContext ctx) {
                                    log.info( "open handler" );
                                    getHandler().initClient( ctx.channel().id().asShortText(), ctx );
                                }
                            } );
                        }
                    } );

            ChannelFuture f = bootstrap.connect( serverAddr, serverPort ).sync();
            f.channel().closeFuture().sync();

        } catch (InterruptedException e) {
            log.warn( "{} -> Interrupt error: {}", server.getClientId(), e.getMessage() );
            server.disConnect( getHandler() );
        } catch (Exception e) {
            if (e.getCause() != null) {
                log.warn( "{} -> Connect error: {}", server.getClientId(), e.getCause().getMessage() );
            }
            server.disConnect( getHandler() );
        }
        log.debug( "{} -> Client thread stopped", server.getClientId() );
    }
}
