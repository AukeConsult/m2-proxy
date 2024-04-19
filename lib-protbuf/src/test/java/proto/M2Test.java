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

        byte[] content = m.getSubMessage().toByteArray();
        MessageOuterClass.Message m_in = MessageOuterClass.Message.parseFrom(content);
        assertEquals(m.getPing(),m_in.getPing());

    }

    @Test
    void Request () throws InvalidProtocolBufferException {

        MessageOuterClass.Request r = MessageOuterClass.Request
                .newBuilder()
                .setRequestId(10)
                .setSessionId(22)
                .build();

        MessageOuterClass.Message m = MessageOuterClass.Message.newBuilder()
                .setType(MessageOuterClass.MessageType.REQUEST)
                .setSubMessage(r.toByteString())
                .build();

        byte[] content = m.toByteArray();

        MessageOuterClass.Message m_in = MessageOuterClass.Message.parseFrom(content);
        MessageOuterClass.Request r2 = MessageOuterClass.Request.parseFrom(m_in.getSubMessage().toByteArray());
        assertEquals(r.getRequestId(),r2.getRequestId());;

    }


}
