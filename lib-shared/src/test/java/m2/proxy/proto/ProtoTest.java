//package m2.proxy.proto;
//
//import org.junit.jupiter.api.Test;
//
//import java.util.Arrays;
//
//import example.complex.Complex.*;
//
//public class ProtoTest {
//
//    public static DummyMessage newDummyMessage(Integer id, String name){
//        // same learning as "SimpleMain"
//        DummyMessage.Builder dummyMessageBuilder = DummyMessage.newBuilder();
//        DummyMessage message =  dummyMessageBuilder.setName(name)
//                .setId(id)
//                .build();
//
//        return message;
//    }
//
//    @Test
//    void simpletest() {
//
//        ComplexMessage.Builder builder = ComplexMessage.newBuilder();
//
//        // singular message field
////        builder.setOneDummy(oneDummy);
//
//        // repeated field
//        builder.addMultipleDummy(newDummyMessage(66, "second dummy"));
//        builder.addMultipleDummy(newDummyMessage(67, "third dummy"));
//        builder.addMultipleDummy(newDummyMessage(68, "fourth dummy"));
//
//        builder.addAllMultipleDummy(Arrays.asList(
//                newDummyMessage(69, "other dummy"),
//                newDummyMessage(70, "other other dummy")
//        ));
//
//        ComplexMessage message = builder.build();
//
//    }
//
//
//}
