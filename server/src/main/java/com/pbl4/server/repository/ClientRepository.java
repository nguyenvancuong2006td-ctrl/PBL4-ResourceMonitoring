package com.pbl4.server.repository;

import com.pbl4.server.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, String> {

    Optional<Client> findByIpAddress(String ipAddress);
}