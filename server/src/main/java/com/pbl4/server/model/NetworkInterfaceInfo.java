package com.pbl4.server.model;

import com.pbl4.server.enums.NetworkInterfaceStatus;
import jakarta.persistence.*;

@Entity
@Table(
    name = "network_interface_info",
    uniqueConstraints = {
        @UniqueConstraint(
            columnNames = {
                "client_id",
                "interface_name"
            }
        )
    }
)
public class NetworkInterfaceInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "interface_id")
    private Long interfaceId;

    @ManyToOne
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "interface_name", length = 50)
    private String interfaceName;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "mac_address", length = 50)
    private String macAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private NetworkInterfaceStatus status;

    @Column(name = "description")
    private String description;

    public NetworkInterfaceInfo() {
    }

    public Long getInterfaceId() {
        return interfaceId;
    }

    public void setInterfaceId(Long interfaceId) {
        this.interfaceId = interfaceId;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public NetworkInterfaceStatus getStatus() {
        return status;
    }

    public void setStatus(NetworkInterfaceStatus status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}