package biz.ugur.busroutebackend.admin.application.dto.admin;

import biz.ugur.busroutebackend.admin.domain.model.Admin;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

public record AdminResult(
    String id,
    String username,
    String fullName,
    String avatar,
    Boolean isActive,
    Boolean isSuperAdmin,
    LocalDateTime createdAt,
    LocalDateTime lastLoginAt

) {

    public static AdminResult fromDomain(Admin admin) {
        return new AdminResult(
                admin.getId().getValue(),
                admin.getUsername(),
                admin.getFullName(),
                admin.getAvatar(),
                admin.getIsActive(),
                admin.getIsSuperAdmin(),
                admin.getCreatedAt(),
                admin.getLastLoginAt()
        );
    }


}
