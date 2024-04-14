package m2.proxy.proto;



public class KryoHandler {

    public KryoHandler() {}

//    public byte[] encode(Object obj) {
//
//        ByteArrayOutputStream b = new ByteArrayOutputStream();
//        Output out = new Output(b);
//        kryo.writeClassAndObject(out,obj);
//        return out.getBuffer();
//
//    }
//    public Object decode(byte[] bytes) {
//        ByteArrayInputStream b = new ByteArrayInputStream(bytes);
//        Input in = new Input(b);
//        return kryo.readClassAndObject(in);
//    }

}
