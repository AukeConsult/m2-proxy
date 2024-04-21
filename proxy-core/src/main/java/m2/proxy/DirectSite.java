package m2.proxy;

public class DirectSite {
    public String path;
    public String destination;
    DirectSite(String path, String destination) {
        this.path=path;
        this.destination=destination;
    }
}