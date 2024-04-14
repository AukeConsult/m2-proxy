package proto;

import com.example.options.OptionMessageOther;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import org.junit.jupiter.api.Test;
import proto.complex.Complex;
import proto.enumerations.EnumExample;
import proto.simple.Simple;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class ProtTest {

    public static Complex.DummyMessage newDummyMessage(Integer id, String name){
        // same learning as "SimpleMain"
        Complex.DummyMessage.Builder dummyMessageBuilder = Complex.DummyMessage.newBuilder();
        Complex.DummyMessage message =  dummyMessageBuilder.setName(name)
                .setId(id)
                .build();

        return message;
    }

    @Test
    void Complexmain () {

        System.out.println("Complex example");

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

        System.out.println(message.toString());

        // GET EXAMPLE
        // message.getMultipleDummyList();

    }

    @Test
    void EnumMain() {

        System.out.println("Example for Enums");

        EnumExample.EnumMessage.Builder builder = EnumExample.EnumMessage.newBuilder();

        builder.setId(345);

        // example with Enums
        builder.setDayOfTheWeek(EnumExample.DayOfTheWeek.FRIDAY);

        EnumExample.EnumMessage message = builder.build();

        System.out.println(message);

    }

    @Test
    void OptionsMain() {
        OptionMessageOther other = OptionMessageOther.newBuilder().build();
    }

    @Test
    void ProtoToJSONMain() throws InvalidProtocolBufferException {

        Simple.SimpleMessage.Builder builder = Simple.SimpleMessage.newBuilder();

        // simple fields
        builder.setId(42)  // set the id field
                .setIsSimple(true)  // set the is_simple field
                .setName("My Simple Message Name"); // set the name field

        // repeated field
        builder.addSampleList(1)
                .addSampleList(2)
                .addSampleList(3)
                .addAllSampleList(Arrays.asList(4, 5, 6));


        // Print this as a JSON
        String jsonString = JsonFormat.printer()
                // .includingDefaultValueFields() - options
                .print(builder);
        System.out.println(jsonString);


        // parse JSON into Protobuf
        Simple.SimpleMessage.Builder builder2 = Simple.SimpleMessage.newBuilder();

        JsonFormat.parser()
                .ignoringUnknownFields()
                .merge(jsonString, builder2);

        System.out.println(builder2);
    }

    @Test
    void SimpleMain() {
        System.out.println("Hello world!");

        Simple.SimpleMessage.Builder builder = Simple.SimpleMessage.newBuilder();

        // simple fields
        builder.setId(42)  // set the id field
                .setIsSimple(true)  // set the is_simple field
                .setName("My Simple Message Name"); // set the name field

        // repeated field
        builder.addSampleList(1)
                .addSampleList(2)
                .addSampleList(3)
                .addAllSampleList(Arrays.asList(4, 5, 6));

        System.out.println(builder.toString());

        Simple.SimpleMessage message = builder.build();

        // write the protocol buffers binary to a file
        try {
            FileOutputStream outputStream = new FileOutputStream("simple_message.bin");
            message.writeTo(outputStream);
            outputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // send as byte array
        // byte[] bytes = message.toByteArray();

        try {
            System.out.println("Reading from file... ");
            FileInputStream fileInputStream = new FileInputStream("simple_message.bin");
            Simple.SimpleMessage messageFromFile = Simple.SimpleMessage.parseFrom(fileInputStream);
            System.out.println(messageFromFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
