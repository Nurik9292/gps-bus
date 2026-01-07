package biz.ugur.busroutebackend.admin.domain.model;

import biz.ugur.busroutebackend.admin.domain.events.AdminCreatedEvent;
import biz.ugur.busroutebackend.admin.domain.events.AdminPasswordChangedEvent;
import biz.ugur.busroutebackend.admin.domain.events.AdminProfileUpdatedEvent;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import biz.ugur.busroutebackend.shared.domain.services.PasswordEncoder;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.Objects;

@Builder(toBuilder = true)
@Getter
@ToString
@EqualsAndHashCode(callSuper = false)
public class Admin extends AggregateRoot<Admin, AdminId> {

    private final AdminId id;
    private final String username;
    private final String passwordHash;
    private final String fullName;
    private final String avatar;
    private final Boolean isActive;
    private final Boolean isSuperAdmin;
    private final LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;


    public static Admin create(String username,
                               String encodedPassword,
                               String fullName,
                               String avatar,
                               Boolean isSuperAdmin,
                               Boolean isActive) {
        LocalDateTime now = LocalDateTime.now();

        Admin admin = builder()
                .id(AdminId.generate())
                .username(username)
                .passwordHash(encodedPassword)
                .fullName(fullName)
                .isSuperAdmin(isSuperAdmin)
                .isActive(isActive)
                .lastLoginAt(now)
                .avatar(avatar)
                .build();

        admin.createdAt = now;
        admin.updatedAt = now;
        admin.version = 0L;

        admin.registerEvent(new AdminCreatedEvent(
                admin.id.getValue(),
                admin.username,
                admin.fullName,
                admin.isSuperAdmin
        ));

        return admin;
    }


    public static Admin fromDatabase(
            AdminId id,
            String username,
            String passwordHash,
            String fullName,
            String avatar,
            Boolean isActive,
            Boolean isSuperAdmin,
            LocalDateTime lastLoginAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long version
    ) {
        Admin admin = builder()
                .id(id)
                .username(username)
                .passwordHash(passwordHash)
                .fullName(fullName)
                .avatar(avatar)
                .isActive(isActive)
                .isSuperAdmin(isSuperAdmin)
                .lastLoginAt(lastLoginAt)
                .build();
        admin.createdAt = createdAt;
        admin.updatedAt = updatedAt;
        admin.version = version;
        return  admin;
    }


    public Admin changePassword(String encodedNewPassword) {
        Admin updatedAdmin = this.toBuilder()
                .passwordHash(encodedNewPassword)
                .build();

        updatedAdmin.createdAt = this.createdAt;
        updatedAdmin.updatedAt = this.updatedAt;
        updatedAdmin.version = this.version;

        updatedAdmin.registerEvent(new AdminPasswordChangedEvent(
                this.id.getValue(),
                this.username
        ));

        return updatedAdmin;
    }

    public boolean checkPassword(String rawPassword, PasswordEncoder encoder) {
        return encoder.matches(rawPassword, this.passwordHash);
    }

    public Admin updateLastLogin() {
        Admin updatedAdmin = this.toBuilder()
                .lastLoginAt(LocalDateTime.now())
                .build();

        updatedAdmin.createdAt = this.createdAt;
        updatedAdmin.updatedAt = this.updatedAt;
        updatedAdmin.version = this.version;

        return updatedAdmin;
    }

    public Admin deactivate() {
        if (Boolean.FALSE.equals(this.isActive)) {
            return this;
        }

        Admin updatedAdmin = this.toBuilder()
                .isActive(false)
                .build();

        updatedAdmin.createdAt = this.createdAt;
        updatedAdmin.updatedAt = this.updatedAt;
        updatedAdmin.version = this.version;

        return updatedAdmin;
    }

    public Admin activate() {
        if (Boolean.TRUE.equals(this.isActive)) {
            return this;
        }

        Admin updatedAdmin = this.toBuilder()
                .isActive(true)
                .build();

        updatedAdmin.createdAt = this.createdAt;
        updatedAdmin.updatedAt = this.updatedAt;
        updatedAdmin.version = this.version;

        return updatedAdmin;
    }

    public Admin updateAvatar(String avatar) {
        if (Objects.equals(avatar, this.avatar)) {
            return this;
        }

        Admin updatedAdmin = this.toBuilder()
                .avatar(avatar)
                .build();

        updatedAdmin.createdAt = this.createdAt;
        updatedAdmin.updatedAt = this.updatedAt;
        updatedAdmin.version = this.version;

        updatedAdmin.registerEvent(new AdminProfileUpdatedEvent(
                this.id.getValue(),
                this.username,
                this.fullName,
                avatar,
                true
        ));

        return updatedAdmin;
    }


    public Admin removeAvatar() {
        if (this.avatar == null) {
            return this;
        }

        Admin updatedAdmin = this.toBuilder()
                .avatar(null)
                .build();

        updatedAdmin.createdAt = this.createdAt;
        updatedAdmin.updatedAt = this.updatedAt;
        updatedAdmin.version = this.version;

        updatedAdmin.registerEvent(new AdminProfileUpdatedEvent(
                this.id.getValue(),
                this.username,
                this.fullName,
                null,
                true
        ));

        return updatedAdmin;
    }


    public Admin updateProfile(String username, String fullName) {
        String newUsername = this.username;
        String newFullName = this.fullName;
        boolean changed = false;

        if (username != null && !username.trim().isEmpty()) {
            newUsername = username.trim();
            changed = true;
        }

        if (fullName != null && !fullName.trim().isEmpty()) {
            newFullName = fullName.trim();
            changed = true;
        }

        if (!changed) {
            return this;
        }

        Admin updatedAdmin = this.toBuilder()
                .username(newUsername)
                .fullName(newFullName)
                .build();

        updatedAdmin.createdAt = this.createdAt;
        updatedAdmin.updatedAt = this.updatedAt;
        updatedAdmin.version = this.version;

        updatedAdmin.registerEvent(new AdminProfileUpdatedEvent(
                updatedAdmin.id.getValue(),
                updatedAdmin.username,
                updatedAdmin.fullName,
                updatedAdmin.avatar,
                false
        ));

        return updatedAdmin;
    }


    public Admin updateProfile(String username, String fullName, String avatar) {
        String newUsername = this.username;
        String newFullName = this.fullName;
        String newAvatar = this.avatar;
        boolean changed = false;
        boolean avatarChanged = false;

        if (username != null && !username.trim().isEmpty()) {
            newUsername = username.trim();
            changed = true;
        }

        if (fullName != null && !fullName.trim().isEmpty() && !fullName.equals(this.fullName)) {
            newFullName = fullName.trim();
            changed = true;
        }

        if (!Objects.equals(avatar, this.avatar)) {
            newAvatar = avatar;
            changed = true;
            avatarChanged = true;
        }

        if (!changed) {
            return this;
        }

        Admin updatedAdmin = this.toBuilder()
                .username(newUsername)
                .fullName(newFullName)
                .avatar(newAvatar)
                .build();

        updatedAdmin.createdAt = this.createdAt;
        updatedAdmin.updatedAt = this.updatedAt;
        updatedAdmin.version = this.version;

        updatedAdmin.registerEvent(new AdminProfileUpdatedEvent(
                updatedAdmin.id.getValue(),
                updatedAdmin.username,
                updatedAdmin.fullName,
                updatedAdmin.avatar,
                avatarChanged
        ));

        return updatedAdmin;
    }


    @Override
    public AdminId getId() {
        return id;
    }

    @Override
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public Long getVersion() {
        return version;
    }

    @Override
    public void setVersion(Long version) {
        this.version = version;
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