package biz.ugur.busroutebackend.admin.domain.model;

import biz.ugur.busroutebackend.admin.domain.events.AdminCreatedEvent;
import biz.ugur.busroutebackend.admin.domain.events.AdminPasswordChangedEvent;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.domain.AggregateRoot;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Getter
@Table("admins")
public class Admin extends AggregateRoot<Admin, AdminId> {

    @Id
    @Column("id")
    private AdminId id;

    @Column("username")
    private String username;

    @Column("password_hash")
    private String passwordHash;

    @Column("full_name")
    private String fullName;

    @Column("is_active")
    private Boolean isActive;

    @Column("is_super_admin")
    private Boolean isSuperAdmin;

    @Column("last_login_at")
    private java.time.Instant lastLoginAt;

    private static final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public Admin(String username, String password, String fullName, Boolean isSuperAdmin) {
        this.id = AdminId.generate();
        this.username = validateUsername(username);
        this.passwordHash = passwordEncoder.encode(password);
        this.fullName = fullName;
        this.isActive = true;
        this.isSuperAdmin = isSuperAdmin != null ? isSuperAdmin : false;

        registerEvent(new AdminCreatedEvent(
                this.id.getValue(),
                this.username,
                this.fullName,
                this.isSuperAdmin
        ));
    }

    public Admin(AdminId id, String username, String passwordHash, String fullName,
                 Boolean isActive, Boolean isSuperAdmin, java.time.Instant lastLoginAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.isActive = isActive;
        this.isSuperAdmin = isSuperAdmin;
        this.lastLoginAt = lastLoginAt;
    }

    public void changePassword(String newPassword) {
        this.passwordHash = passwordEncoder.encode(newPassword);

        registerEvent(new AdminPasswordChangedEvent(
                this.id.getValue(),
                this.username
        ));
    }

    public boolean checkPassword(String password) {
        return passwordEncoder.matches(password, this.passwordHash);
    }

    public void updateLastLogin() {
        this.lastLoginAt = java.time.Instant.now();
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }

    public void updateProfile(String fullName) {
        if (fullName != null && !fullName.trim().isEmpty()) {
            this.fullName = fullName.trim();
        }
    }

    @Override
    public AdminId getId() {
        return id;
    }

    private String validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }

        String cleaned = username.trim().toLowerCase();
        if (!cleaned.matches("^[a-z0-9_]{3,20}$")) {
            throw new IllegalArgumentException("Username must be 3-20 characters, alphanumeric and underscore only");
        }
        return cleaned;
    }
}