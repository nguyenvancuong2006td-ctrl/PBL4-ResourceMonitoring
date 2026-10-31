package pbl4.agent.monitoring;

import oshi.SystemInfo;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;

import pbl4.agent.model.DiskData;

import java.util.ArrayList;
import java.util.List;

public class DiskMonitorService {

    private final SystemInfo systemInfo;

    public DiskMonitorService() {
        systemInfo = new SystemInfo();
    }

    /**
     * Lấy danh sách hệ thống tệp/điểm mount.
     */
    public List<DiskData> collectDiskData() {

        List<DiskData> diskDataList = new ArrayList<>();

        FileSystem fileSystem =
                systemInfo.getOperatingSystem()
                        .getFileSystem();

        for (OSFileStore fileStore :
                fileSystem.getFileStores()) {

            long totalSpace = fileStore.getTotalSpace();
            long usableSpace = fileStore.getUsableSpace();

            if (totalSpace <= 0) {
                continue;
            }

            long usedSpace = Math.max(
                    0L,
                    totalSpace - usableSpace
            );

            double usagePercent =
                    ((double) usedSpace / totalSpace) * 100.0;

            DiskData diskData = new DiskData();

            diskData.setName(fileStore.getName());
            diskData.setMountPoint(fileStore.getMount());
            diskData.setTotalSpace(totalSpace);
            diskData.setUsedSpace(usedSpace);
            diskData.setUsableSpace(usableSpace);
            diskData.setUsagePercent(usagePercent);

            diskDataList.add(diskData);
        }

        return diskDataList;
    }

    /**
     * Tổng dung lượng các hệ thống tệp OSHI phát hiện.
     */
    public long getTotalSpace() {

        long totalSpace = 0;

        for (OSFileStore fileStore : getFileStores()) {
            totalSpace += Math.max(
                    0L,
                    fileStore.getTotalSpace()
            );
        }

        return totalSpace;
    }

    /**
     * Tổng dung lượng còn khả dụng.
     */
    public long getUsableSpace() {

        long usableSpace = 0;

        for (OSFileStore fileStore : getFileStores()) {
            usableSpace += Math.max(
                    0L,
                    fileStore.getUsableSpace()
            );
        }

        return usableSpace;
    }

    /**
     * Tổng dung lượng đã sử dụng.
     */
    public long getUsedSpace() {

        return Math.max(
                0L,
                getTotalSpace() - getUsableSpace()
        );
    }

    /**
     * Phần trăm dung lượng đã sử dụng.
     */
    public double getDiskUsage() {

        long totalSpace = getTotalSpace();

        if (totalSpace <= 0) {
            return 0.0;
        }

        return ((double) getUsedSpace() / totalSpace) * 100.0;
    }

    /**
     * Lấy danh sách hệ thống tệp để tái sử dụng.
     */
    private List<OSFileStore> getFileStores() {

        FileSystem fileSystem =
                systemInfo.getOperatingSystem()
                        .getFileSystem();

        return fileSystem.getFileStores();
    }
}