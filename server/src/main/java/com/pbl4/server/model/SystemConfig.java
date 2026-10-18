package com.pbl4.server.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_configs")
public class SystemConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "config_id")
    private Long configId;

    @Column(name = "config_key", length = 20, nullable = false, unique = true)
    private String configKey;

    @Column(name = "collection_interval_seconds", nullable = false)
    private Integer collectionIntervalSeconds;

    @Column(name = "heartbeat_interval_seconds", nullable = false)
    private Integer heartbeatIntervalSeconds;

    @Column(name = "heartbeat_timeout_seconds", nullable = false)
    private Integer heartbeatTimeoutSeconds;

    @ManyToOne
    @JoinColumn(name = "updated_by")
    private Account updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public SystemConfig() {
    }

    public Long getConfigId() {
        return configId;
    }

    public void setConfigId(Long configId) {
        this.configId = configId;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public Integer getCollectionIntervalSeconds() {
        return collectionIntervalSeconds;
    }

    public void setCollectionIntervalSeconds(Integer collectionIntervalSeconds) {
        this.collectionIntervalSeconds = collectionIntervalSeconds;
    }

    public Integer getHeartbeatIntervalSeconds() {
        return heartbeatIntervalSeconds;
    }

    public void setHeartbeatIntervalSeconds(Integer heartbeatIntervalSeconds) {
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
    }

    public Integer getHeartbeatTimeoutSeconds() {
        return heartbeatTimeoutSeconds;
    }

    public void setHeartbeatTimeoutSeconds(Integer heartbeatTimeoutSeconds) {
        this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds;
    }

    public Account getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Account updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}