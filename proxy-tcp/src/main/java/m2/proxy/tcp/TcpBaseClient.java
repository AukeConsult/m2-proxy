package m2.proxy.tcp;

import com.google.protobuf.ByteString;
import io.netty.channel.*;
import m2.proxy.tcp.handlers.ClientHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.m2.MessageOuterClass.*;

import java.util.Random;

public class TcpBaseClient extends TcpBaseClientBase {
    private static final Logger log = LoggerFactory.getLogger(TcpBaseClient.class);

    public TcpBaseClient(String clientId, String serverAddr, int ServerPort, String localAddress) {
        super(clientId,serverAddr,ServerPort,localAddress);
    }

    public TcpBaseClient(String serverHost, int ServerPort, String bindAddress) {
        this(null, serverHost, ServerPort, bindAddress);
    }

    @Override
    public ClientHandler setClientHandler(String channelId, ChannelHandlerContext ctx) {

        log.info("set client handler");
        return new ClientHandler(this, channelId, ctx) {
            @Override
            public void onRequest(long sessionId, long requestId, RequestType type, ByteString requestMessage) {
                try {
                    if(type==RequestType.PLAIN) {
                        reply(sessionId,requestId,type,ByteString.copyFromUtf8("hello back"));
                    } else if (type==RequestType.HTTP ) {

                        reply (
                                sessionId,
                                requestId,
                                type,
                                HttpReply.newBuilder().setHeader(ByteString.copyFromUtf8("header"))
                                        .setBody(ByteString.copyFromUtf8("body"))
                                        .build().toByteString()

                        );

                    } else {
                        reply(sessionId,
                                requestId,
                                RequestType.NONE,
                                null
                        );
                    }

                    Thread.sleep(new Random().nextInt(2000));

                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}
