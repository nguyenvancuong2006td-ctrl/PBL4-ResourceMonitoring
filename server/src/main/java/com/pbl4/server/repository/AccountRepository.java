
package com.pbl4.server.repository;

import com.pbl4.server.enums.RoleName;
import com.pbl4.server.model.Account;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    // ==================================================
    // 1. TÌM TÀI KHOẢN THEO USERNAME
    // ==================================================

    /**
     * Tìm tài khoản theo username, đồng thời tải:
     * - Role
     * - Permissions của Role
     *
     * Phục vụ xác thực JWT và phân quyền.
     */
    @EntityGraph(attributePaths = {
            "role",
            "role.permissions"
    })
    @Query("""
            SELECT a
            FROM Account a
            WHERE a.username = :username
            """)
    Optional<Account> findByUsernameWithRoleAndPermissions(
            @Param("username") String username
    );

    // ==================================================
    // 2. TÌM TÀI KHOẢN THEO USERNAME
    // ==================================================

    Optional<Account> findByUsername(String username);

    // ==================================================
    // 3. TÌM TÀI KHOẢN THEO EMAIL
    // ==================================================

    Optional<Account> findByEmail(String email);

    // ==================================================
    // 4. KIỂM TRA USERNAME ĐÃ TỒN TẠI
    // ==================================================

    boolean existsByUsername(String username);

    // ==================================================
    // 5. KIỂM TRA EMAIL ĐÃ TỒN TẠI
    // ==================================================

    boolean existsByEmail(String email);

    // ==================================================
    // 6. KIỂM TRA TÀI KHOẢN THEO ROLE
    // ==================================================

    boolean existsByRole_RoleName(RoleName roleName);
}
