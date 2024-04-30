package m2.proxy.common;

import m2.proxy.common.*;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public abstract class LocalForward implements Service {

    HttpHelper httpHelper = new HttpHelper();

    public LocalForward() { }
    public Optional<RawHttpResponse<?>> handleHttp(RawHttpRequest request) throws HttpException {

        try {

            String body = "";
            if (request.getBody().isPresent()) {
                body = request.getBody().map( httpHelper.decodeBody() ).orElse( "" );
            }
            final String path = request.getStartLine().getUri().getPath();
            Map<String, String> headers = new HashMap<>();
            request.getHeaders().forEach( (key, value) -> headers.put( key, value ) );
            String contentType = headers.getOrDefault( "Content-Type", "text/plain" );
            Optional<ContentResult> result = onHandlePath( request.getMethod(), path, headers, contentType, body );
            if (result.isPresent()) {
                return httpHelper.response( result.get() );
            } else {
                return Optional.empty();
            }
        } catch (Exception e) {
            throw new HttpException( ProxyStatus.FAIL, e.getMessage() );
        }
    }

    private Service service;
    @Override
    public Service getService() { return service; }
    @Override
    public void setService(Service service) { this.service = service; }

    protected abstract Optional<ContentResult> onHandlePath(String verb, String path, Map<String, String> headers, String contentType, String body)
            throws HttpException;
}
