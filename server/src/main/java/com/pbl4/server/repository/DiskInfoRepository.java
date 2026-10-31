package com.pbl4.server.repository;

import com.pbl4.server.model.Client;
import com.pbl4.server.model.DiskInfo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DiskInfoRepository
        extends JpaRepository<DiskInfo, Long> {

    List<DiskInfo> findByClientClientId(String clientId);

    Optional<DiskInfo> findByClientAndDeviceNameAndMountPoint(
            Client client,
            String deviceName,
            String mountPoint
    );
}