package proto;


import com.google.protobuf.InvalidProtocolBufferException;


import org.junit.jupiter.api.Test;
import proto.m2.MessageOuterClass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class M2Test {


    @Test
    void PingTest () throws InvalidProtocolBufferException {

        MessageOuterClass.Message m = MessageOuterClass.Message.newBuilder()
                .setType(MessageOuterClass.MessageType.PING)
                .setRequestId(100L)
                .setPing(MessageOuterClass.Ping.newBuilder().setId(100).build())
                .build();

        byte[] content = m.toByteArray();
        MessageOuterClass.Message m_in = MessageOuterClass.Message.parseFrom(content);

        assertEquals(m.getRequestId(),m_in.getRequestId());
        assertEquals(m.getPing(),m_in.getPing());

        MessageOuterClass.HttpRequest x = m_in.getRequest();
        x.getSerializedSize();
        assertEquals(0,x.getSerializedSize());


    }


}
