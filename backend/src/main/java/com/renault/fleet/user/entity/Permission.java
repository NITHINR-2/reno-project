package com.renault.fleet.user.entity;

import com.renault.fleet.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Reference data seeded by Flyway (V6); codes are granted as Spring Security authorities. */
@Entity
@Table(name = "permissions")
public class Permission extends BaseEntity {

    @Column(nullable = false, length = 60, unique = true)
    private String code;

    @Column(length = 500)
    private String description;

    protected Permission() {
    }

    public String getCode() { return code; }
    public String getDescription() { return description; }
}
