package pbl4.agent.model;

public class NetworkData {

    private String interfaceName;
    private String displayName;
    private long bytesReceived;
    private long bytesSent;

    public NetworkData() {
    }

    public NetworkData(
            String interfaceName,
            String displayName,
            long bytesReceived,
            long bytesSent) {

        this.interfaceName = interfaceName;
        this.displayName = displayName;
        this.bytesReceived = bytesReceived;
        this.bytesSent = bytesSent;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public long getBytesReceived() {
        return bytesReceived;
    }

    public void setBytesReceived(long bytesReceived) {
        this.bytesReceived = bytesReceived;
    }

    public long getBytesSent() {
        return bytesSent;
    }

    public void setBytesSent(long bytesSent) {
        this.bytesSent = bytesSent;
    }
}