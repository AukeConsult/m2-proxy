package m2.proxy.common;

public class TcpException extends Throwable {

    private final ProxyStatus status;
    public ProxyStatus getStatus() { return status;}
    public TcpException(ProxyStatus status, String message) {
        super(message);
        this.status=status;
    }
    @Override
    public String getMessage() {
        return status.toString() + ":" + super.getMessage();
    }
}
