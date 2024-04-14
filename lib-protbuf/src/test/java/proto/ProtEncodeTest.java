package proto;


import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;
import proto.complex.*;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProtEncodeTest {

    public static Complex.DummyMessage newDummyMessage(Integer id, String name){
        // same learning as "SimpleMain"
        Complex.DummyMessage.Builder dummyMessageBuilder = Complex.DummyMessage.newBuilder();
        Complex.DummyMessage message =  dummyMessageBuilder.setName(name)
                .setId(id)
                .build();

        return message;
    }

    @Test
    void Complexmain () throws InvalidProtocolBufferException {

        Complex.DummyMessage oneDummy = newDummyMessage(55, "one dummy message");
        Complex.ComplexMessage.Builder builder = Complex.ComplexMessage.newBuilder();
        // singular message field
        builder.setOneDummy(oneDummy);

        // repeated field
        builder.addMultipleDummy(newDummyMessage(66, "second dummy"));
        builder.addMultipleDummy(newDummyMessage(67, "third dummy"));
        builder.addMultipleDummy(newDummyMessage(68, "fourth dummy"));

        builder.addAllMultipleDummy(Arrays.asList(
                newDummyMessage(69, "other dummy"),
                newDummyMessage(70, "other other dummy")
        ));
        Complex.ComplexMessage message = builder.build();

        byte[] content = message.toByteArray();
        Complex.ComplexMessage messageIn = Complex.ComplexMessage.parseFrom(content);

        Complex.DummyMessage in = message.getOneDummy();
        Complex.DummyMessage out = messageIn.getOneDummy();

        assertEquals(in.getId(),out.getId());
        assertEquals(in.getName(),out.getName());

        assertEquals(message.getMultipleDummyCount(),messageIn.getMultipleDummyCount());


    }


}
