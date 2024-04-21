package m2.proxy.common;

public class HttpException extends Throwable {

    private final ProxyStatus status;
    public ProxyStatus getStatus() { return status;}
    public HttpException(ProxyStatus status, String message) {
        super(message);
        this.status=status;
    }
    @Override
    public String getMessage() {
        return getStatus().toString() + ":" + super.getMessage();
    }
}
