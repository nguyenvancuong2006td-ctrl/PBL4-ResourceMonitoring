package pbl4.agent.model;

public class DiskData {

    private String name;
    private String mountPoint;

    private long totalSpace;
    private long usedSpace;
    private long usableSpace;

    private double usagePercent;

    public DiskData() {
    }

    public DiskData(
            String name,
            String mountPoint,
            long totalSpace,
            long usedSpace,
            long usableSpace,
            double usagePercent) {

        this.name = name;
        this.mountPoint = mountPoint;
        this.totalSpace = totalSpace;
        this.usedSpace = usedSpace;
        this.usableSpace = usableSpace;
        this.usagePercent = usagePercent;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMountPoint() {
        return mountPoint;
    }

    public void setMountPoint(String mountPoint) {
        this.mountPoint = mountPoint;
    }

    public long getTotalSpace() {
        return totalSpace;
    }

    public void setTotalSpace(long totalSpace) {
        this.totalSpace = totalSpace;
    }

    public long getUsedSpace() {
        return usedSpace;
    }

    public void setUsedSpace(long usedSpace) {
        this.usedSpace = usedSpace;
    }

    public long getUsableSpace() {
        return usableSpace;
    }

    public void setUsableSpace(long usableSpace) {
        this.usableSpace = usableSpace;
    }

    public double getUsagePercent() {
        return usagePercent;
    }

    public void setUsagePercent(double usagePercent) {
        this.usagePercent = usagePercent;
    }
}