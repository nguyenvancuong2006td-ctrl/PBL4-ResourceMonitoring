package com.pbl4.server.model;

import com.pbl4.server.enums.PermissionCode;
import jakarta.persistence.*;

@Entity
@Table(
    name = "permissions",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_permission_code",
            columnNames = "permission_code"
        )
    }
)
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "permission_id")
    private Long permissionId;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "permission_code",
        length = 50,
        nullable = false
    )
    private PermissionCode permissionCode;

    @Column(name = "description")
    private String description;

    public Permission() {
    }

    public Long getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(Long permissionId) {
        this.permissionId = permissionId;
    }

    public PermissionCode getPermissionCode() {
        return permissionCode;
    }

    public void setPermissionCode(PermissionCode permissionCode) {
        this.permissionCode = permissionCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}