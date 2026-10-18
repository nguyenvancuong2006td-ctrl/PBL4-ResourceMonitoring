package com.pbl4.server.model;

import jakarta.persistence.*;

@Entity
@Table(
    name = "disk_info",
    uniqueConstraints = {
        @UniqueConstraint(
            columnNames = {
                "client_id",
                "device_name",
                "mount_point"
            }
        )
    }
)
public class DiskInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "disk_id")
    private Long diskId;

    @ManyToOne
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "device_name", length = 50)
    private String deviceName;

    @Column(name = "mount_point", length = 100)
    private String mountPoint;

    @Column(name = "file_system", length = 50)
    private String fileSystem;

    @Column(name = "total_bytes")
    private Long totalBytes;

    public DiskInfo() {
    }

    public Long getDiskId() {
        return diskId;
    }

    public void setDiskId(Long diskId) {
        this.diskId = diskId;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getMountPoint() {
        return mountPoint;
    }

    public void setMountPoint(String mountPoint) {
        this.mountPoint = mountPoint;
    }

    public String getFileSystem() {
        return fileSystem;
    }

    public void setFileSystem(String fileSystem) {
        this.fileSystem = fileSystem;
    }

    public Long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(Long totalBytes) {
        this.totalBytes = totalBytes;
    }
}