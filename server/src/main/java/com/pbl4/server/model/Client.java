package com.pbl4.server.model;

import com.pbl4.server.enums.ClientStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "clients")
public class Client {

    @Id
    @Column(name = "client_id", length = 50)
    private String clientId;

    @Column(name = "hostname", length = 100)
    private String hostname;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "os_name", length = 50)
    private String osName;

    @Column(name = "os_version", length = 50)
    private String osVersion;

    @Column(name = "kernel_version", length = 50)
    private String kernelVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private ClientStatus status;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "boot_time")
    private LocalDateTime bootTime;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "management_note")
    private String managementNote;

    public Client() {
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getOsName() {
        return osName;
    }

    public void setOsName(String osName) {
        this.osName = osName;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    public String getKernelVersion() {
        return kernelVersion;
    }

    public void setKernelVersion(String kernelVersion) {
        this.kernelVersion = kernelVersion;
    }

    public ClientStatus getStatus() {
        return status;
    }

    public void setStatus(ClientStatus status) {
        this.status = status;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public LocalDateTime getBootTime() {
        return bootTime;
    }

    public void setBootTime(LocalDateTime bootTime) {
        this.bootTime = bootTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getManagementNote() {
        return managementNote;
    }

    public void setManagementNote(String managementNote) {
        this.managementNote = managementNote;
    }
}