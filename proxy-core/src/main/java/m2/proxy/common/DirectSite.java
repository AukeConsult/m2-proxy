package m2.proxy.common;

public class DirectSite {
    private final String path;
    private final String destination;
    public String getPath() { return path; }
    public String getDestination() {  return destination; }
    public DirectSite(String path, String destination) {
        this.path = path;
        this.destination = destination;
    }
}