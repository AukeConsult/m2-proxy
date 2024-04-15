package proto;


import com.google.protobuf.InvalidProtocolBufferException;


import org.junit.jupiter.api.Test;
import proto.m2.MessageOuterClass;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class M2Test {

    @Test
    void PingTest () throws InvalidProtocolBufferException {

        MessageOuterClass.Message m = MessageOuterClass.Message.newBuilder()
                .setType(MessageOuterClass.MessageType.PING)
                .setPing(MessageOuterClass.Ping.newBuilder().build())
                .build();

        byte[] content = m.getMessageBytes().toByteArray();
        MessageOuterClass.Message m_in = MessageOuterClass.Message.parseFrom(content);
        assertEquals(m.getPing(),m_in.getPing());

    }

    @Test
    void Http () throws InvalidProtocolBufferException {

        MessageOuterClass.HttpInRequest r = MessageOuterClass.HttpInRequest
                .newBuilder()
                .setRequestId(10)
                .setSessionId(22)
                .setVerb("GET")
                .setHeader("Hello")
                .setHeader("bodyasdasdasdasd")
                .build();

        MessageOuterClass.Message m = MessageOuterClass.Message.newBuilder()
                .setType(MessageOuterClass.MessageType.HTTP_REQUEST)
                .setSubMessage(r.toByteString())
                .build();

        byte[] content = m.toByteArray();

        MessageOuterClass.Message m_in = MessageOuterClass.Message.parseFrom(content);
        MessageOuterClass.HttpInRequest r2 = MessageOuterClass.HttpInRequest.parseFrom(m_in.getSubMessage().toByteArray());
        assertEquals(r.getRequestId(),r2.getRequestId());
        assertEquals(r.getVerb(),r2.getVerb());
        assertEquals(r.getHeader(),r2.getHeader());
        assertEquals(r.getBody(),r2.getBody());

    }


}
