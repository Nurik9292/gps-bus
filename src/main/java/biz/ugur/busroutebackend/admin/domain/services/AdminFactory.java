package biz.ugur.busroutebackend.admin.domain.services;

import biz.ugur.busroutebackend.admin.domain.events.AdminCreatedEvent;
import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.domain.services.PasswordEncoder;

import java.time.LocalDateTime;

public class AdminFactory {

    private final PasswordEncoder passwordEncoder;

    public AdminFactory(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public Admin create(String username,
                        String password,
                        String fullName,
                        String avatar,
                        Boolean isSuperAdmin,
                        Boolean isActive) {
        LocalDateTime now = LocalDateTime.now();

        Admin admin = Admin.builder()
                .id(AdminId.generate())
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .fullName(fullName)
                .isSuperAdmin(isSuperAdmin)
                .isActive(isActive)
                .lastLoginAt(now)
                .avatar(avatar)
                .build();

        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        admin.setVersion(0L);

        admin.registerEvent(new AdminCreatedEvent(
                admin.getId().getValue(),
                admin.getUsername(),
                admin.getFullName(),
                admin.getIsSuperAdmin()
        ));

        return admin;
    }

    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    public boolean matchesPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
