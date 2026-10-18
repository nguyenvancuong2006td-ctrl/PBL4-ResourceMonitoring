package pbl4.agent.monitoring;

import oshi.SystemInfo;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;

public class DiskMonitorService {

    private final SystemInfo systemInfo;

    public DiskMonitorService() {

        systemInfo =
                new SystemInfo();
    }

    public long getTotalSpace() {

        FileSystem fileSystem =
                systemInfo.getOperatingSystem()
                        .getFileSystem();

        long totalSpace = 0;

        for (OSFileStore fileStore :
                fileSystem.getFileStores()) {

            totalSpace += fileStore.getTotalSpace();
        }

        return totalSpace;
    }

    public long getUsableSpace() {

        FileSystem fileSystem =
                systemInfo.getOperatingSystem()
                        .getFileSystem();

        long usableSpace = 0;

        for (OSFileStore fileStore :
                fileSystem.getFileStores()) {

            usableSpace += fileStore.getUsableSpace();
        }

        return usableSpace;
    }

    public long getUsedSpace() {

        long totalSpace =
                getTotalSpace();

        long usableSpace =
                getUsableSpace();

        return totalSpace - usableSpace;
    }

    public double getDiskUsage() {

        long totalSpace =
                getTotalSpace();

        long usableSpace =
                getUsableSpace();

        if (totalSpace == 0) {
            return 0;
        }

        long usedSpace =
                totalSpace - usableSpace;

        return ((double) usedSpace / totalSpace) * 100;
    }
}