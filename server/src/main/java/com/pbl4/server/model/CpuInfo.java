package com.pbl4.server.model;

import jakarta.persistence.*;

@Entity
@Table(name = "cpu_info")
public class CpuInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cpu_info_id")
    private Long cpuInfoId;

    @OneToOne
    @JoinColumn(name = "client_id", nullable = false, unique = true)
    private Client client;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "manufacturer", length = 50)
    private String manufacturer;

    @Column(name = "core_count")
    private Integer coreCount;

    @Column(name = "thread_count")
    private Integer threadCount;

    @Column(name = "frequency_mhz")
    private Double frequencyMhz;

    public CpuInfo() {
    }

    public Long getCpuInfoId() {
        return cpuInfoId;
    }

    public void setCpuInfoId(Long cpuInfoId) {
        this.cpuInfoId = cpuInfoId;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public Integer getCoreCount() {
        return coreCount;
    }

    public void setCoreCount(Integer coreCount) {
        this.coreCount = coreCount;
    }

    public Integer getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(Integer threadCount) {
        this.threadCount = threadCount;
    }

    public Double getFrequencyMhz() {
        return frequencyMhz;
    }

    public void setFrequencyMhz(Double frequencyMhz) {
        this.frequencyMhz = frequencyMhz;
    }
}