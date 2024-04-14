package m2.proxy.proto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class KryoTest {

    KryoHandler handler = new KryoHandler();

    @BeforeEach
    void init() {}

    @Test
    void check_string() {

//        byte[] b = handler.encode("Hello leif");
//        Object ret = handler.decode(b);
//        assertNotNull(ret);
//        assertEquals("Hello leif",ret);

    }

    @Test
    void check_complext() {

//        ComplexMessage msg = new ComplexMessage(
//                "AA",
//                Arrays.asList("asasda","asdasdasd","sasa")
//        );
//
//        byte[] b = handler.encode(msg);
//        ComplexMessage ret = (ComplexMessage)handler.decode(b);
//        assertNotNull(ret);
//        assertNotNull(ret.getLines());
//        assertEquals(msg.getType(),ret.getType());
//        assertEquals(msg.getLines(),ret.getLines());

    }

}
