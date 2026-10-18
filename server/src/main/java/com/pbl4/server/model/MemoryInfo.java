package com.pbl4.server.model;

import jakarta.persistence.*;

@Entity
@Table(name = "memory_info")
public class MemoryInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "memory_info_id")
    private Long memoryInfoId;

    @OneToOne
    @JoinColumn(name = "client_id", nullable = false, unique = true)
    private Client client;

    @Column(name = "total_bytes")
    private Long totalBytes;

    public MemoryInfo() {
    }

    public Long getMemoryInfoId() {
        return memoryInfoId;
    }

    public void setMemoryInfoId(Long memoryInfoId) {
        this.memoryInfoId = memoryInfoId;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public Long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(Long totalBytes) {
        this.totalBytes = totalBytes;
    }
}