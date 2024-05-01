package m2.proxy.server;

import m2.proxy.common.ContentResult;
import m2.proxy.common.HttpException;
import m2.proxy.common.HttpHelper;
import m2.proxy.common.ProxyStatus;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;

import java.util.Optional;

public class ServerSite {

    HttpHelper httpHelper = new HttpHelper();

    private final ProxyServer server;
    public ServerSite(ProxyServer server) {
        this.server=server;
    }

    public Optional<ContentResult> getFrontPage() {

        String page= """
                <!DOCTYPE html><html><body><h1>Casa-IO</h1>
                <h2>Proxy-server</h>
                <p>Hello folks</p>
                <p>Server has #ACTIVE clients</p>
                <table>  
                <tr>    
                <th>ClientId</th>    
                <th>Address</th>    
                <th>Local Address</th>    
                <th>Local port</th>  
                </tr> 
                #CLIENTLIST</table></body></html>
                """;

        StringBuilder list = new StringBuilder();
        list.append( "<tr>" );
        server.getRemoteForward().getActiveClients().forEach( (k,v) -> {
            list.append( "<td>" ).append( v.getRemoteClientId() ).append( "/td>" )
                    .append( "<td>").append( v.getRemotePublicAddress() ).append("/td>" )
                    .append( "<td>").append( v.getRemoteLocalAddress() ).append("/td>" )
                    .append( "<td>").append( v.getRemoteLocalPort() ).append("/td>" );
        });
        list.append( "</tr>" );

        page = page.replace( "#ACTIVE", String.valueOf(  server.getRemoteForward().getClientHandles().size() ))
                .replace("#CLIENTLIST",list.toString());

        return Optional.of(new ContentResult( page ));
    }

    public Optional<RawHttpResponse<?>> handleHttp(RawHttpRequest request) throws HttpException {
        try {

            final String path = request.getStartLine().getUri().getPath();

            Optional<ContentResult> result = Optional.empty();
            if(path.equals( "/" )) {
                result = getFrontPage();
            } else if(path.equals( "/index.html" )) {
                result = getFrontPage();
            }

            if (result.isPresent()) {
                return httpHelper.response(result.get());
            } else {
                return Optional.empty();
            }
        } catch (Exception e) {
            throw new HttpException( ProxyStatus.FAIL, e.getMessage() );
        }
    }

}
