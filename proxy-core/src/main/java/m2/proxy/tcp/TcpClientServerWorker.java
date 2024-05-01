package m2.proxy.tcp;

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

public class TcpClientServerWorker extends ConnectionWorker {
    private static final Logger log = LoggerFactory.getLogger( TcpClientServerWorker.class );

    private final TcpClient tcpClient;
    private final EventLoopGroup bossGroup;
    private final String myAddress;
    private final int myPort;
    private final AtomicReference<ConnectionHandler> connectionHandler = new AtomicReference<>();

    public ConnectionHandler getHandler() {
        if (connectionHandler.get() == null) {
            connectionHandler.set( tcpClient.setConnectionHandler() );
            connectionHandler.get().setTcpServe( tcpClient );
            connectionHandler.get().setConnectionWorker( this );
        }
        return connectionHandler.get();
    }

    public TcpClientServerWorker(final TcpClient tcpClient, String myAddress, int myPort) {
        this.tcpClient = tcpClient;
        this.myAddress = myAddress;
        this.myPort = myPort;
        this.bossGroup = new NioEventLoopGroup();
    }

    @Override
    public void stopWorker() {
        if (running.getAndSet( false )) {
            log.debug( "{} -> Client disconnect", tcpClient.getMyId() );
            bossGroup.shutdownGracefully();
            if (connectionHandler.get() != null) {
                connectionHandler.get().close();
            }
        }
    }

    @Override
    public void run() {
        log.debug( "{} -> server thread started", tcpClient.getMyId() );
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group( bossGroup )
                    .channel( NioSocketChannel.class )
                    .handler( new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addFirst( new MessageDecoder( tcpClient ) );
                            ch.pipeline().addLast( new SimpleChannelInboundHandler<byte[]>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, byte[] bytes) {
                                    tcpClient.getExecutor().execute( () -> {
                                        try {
                                            Message msg = Message.parseFrom( bytes );
                                            getHandler().readMessage( msg );
                                        } catch (InvalidProtocolBufferException e) {
                                            log.error( "error create message from bytes" );
                                        }
                                    });

                                }
                                @Override
                                public void channelActive(ChannelHandlerContext ctx) {
                                    getHandler().initClient( ctx.channel().id().asShortText(), ctx );
                                }
                            } );
                        }
                    } );

            ChannelFuture f = bootstrap.connect( myAddress, myPort ).sync();
            f.channel().closeFuture().sync();

        } catch (InterruptedException e) {
            log.warn( "{} -> Interrupt error: {}", tcpClient.getMyId(), e.getMessage() );
            tcpClient.disConnect( getHandler() );
        } catch (Exception e) {
            if (e.getCause() != null) {
                log.warn( "{} -> Connect error: {}", tcpClient.getMyId(), e.getCause().getMessage() );
            }
            tcpClient.disConnect( getHandler() );
        }
        log.debug( "{} -> Client thread stopped", tcpClient.getMyId() );
    }
}
