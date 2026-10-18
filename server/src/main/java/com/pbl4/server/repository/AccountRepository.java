package com.pbl4.server.repository;

import com.pbl4.server.enums.RoleName;
import com.pbl4.server.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    // Tìm tài khoản theo username
    Optional<Account> findByUsername(String username);

    // Tìm tài khoản theo email
    Optional<Account> findByEmail(String email);

    // Kiểm tra username đã tồn tại chưa
    boolean existsByUsername(String username);

    // Kiểm tra email đã tồn tại chưa
    boolean existsByEmail(String email);

    // Kiểm tra đã tồn tại tài khoản có Role tương ứng chưa
    boolean existsByRole_RoleName(RoleName roleName);
}