package biz.ugur.busroutebackend.admin.infrastructure.persistence.entity;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;


@Builder
@Table("admins")
@Getter
@ToString
@EqualsAndHashCode(callSuper = false)
public class AdminEntity {

    @Id
    @Column("id")
    private String id;

    @Column("username")
    private String username;

    @Column("password_hash")
    private String passwordHash;

    @Column("full_name")
    private String fullName;

    @Column("avatar")
    private String avatar;

    @Column("is_active")
    private Boolean isActive;

    @Column("is_super_admin")
    private Boolean isSuperAdmin;

    @Column("last_login_at")
    private LocalDateTime lastLoginAt;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("version")
    private Long version;
}
