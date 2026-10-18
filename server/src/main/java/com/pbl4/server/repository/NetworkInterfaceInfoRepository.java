package com.pbl4.server.repository;

import com.pbl4.server.model.NetworkInterfaceInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NetworkInterfaceInfoRepository
        extends JpaRepository<NetworkInterfaceInfo, Long> {

    List<NetworkInterfaceInfo> findByClientClientId(String clientId);
}