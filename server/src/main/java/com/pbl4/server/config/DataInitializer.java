package com.pbl4.server.config;

import com.pbl4.server.enums.AccountStatus;
import com.pbl4.server.enums.RoleName;
import com.pbl4.server.model.Account;
import com.pbl4.server.model.Role;
import com.pbl4.server.repository.AccountRepository;
import com.pbl4.server.repository.RoleRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initAdmin(
            AccountRepository accountRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {

        return args -> {

            // Kiểm tra đã có ADMIN chưa
            boolean adminExists =
                    accountRepository.existsByRole_RoleName(
                            RoleName.ADMIN
                    );

            if (adminExists) {
                return;
            }

            // Tìm role ADMIN
            Role adminRole =
                    roleRepository.findByRoleName(RoleName.ADMIN)
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "Chưa có Role ADMIN trong database"
                                    )
                            );

            // Tạo tài khoản ADMIN
            Account admin = new Account();

            admin.setUsername("admin");
            admin.setEmail("admin@pbl4.com");

            // Mật khẩu mặc định
            admin.setPasswordHash(
                    passwordEncoder.encode("Admin@123")
            );

            admin.setRole(adminRole);
            admin.setEmailVerified(true);
            admin.setStatus(AccountStatus.ACTIVE);

            accountRepository.save(admin);

            System.out.println(
                    "===================================="
            );
            System.out.println(
                    "Đã tạo tài khoản ADMIN mặc định"
            );
            System.out.println(
                    "Username: admin"
            );
            System.out.println(
                    "Password: Admin@123"
            );
            System.out.println(
                    "===================================="
            );
        };
    }
}