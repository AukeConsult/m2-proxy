package m2.proxy.server.remote;

public class RemoteSite {
    private final String path;
    private final String destination;
    public String getPath() { return path; }
    public String getDestination() {  return destination; }
    public RemoteSite(String path, String destination) {
        this.path = path;
        this.destination = destination;
    }
}