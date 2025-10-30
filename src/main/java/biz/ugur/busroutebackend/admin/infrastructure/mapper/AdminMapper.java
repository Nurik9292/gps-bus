package biz.ugur.busroutebackend.admin.infrastructure.mapper;

import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.admin.infrastructure.persistence.entity.AdminEntity;

public class AdminMapper {

    private AdminMapper() {}

    public static Admin toDomain(AdminEntity entity) {
        return Admin.fromDatabase(
                AdminId.of(entity.getId()),
                entity.getUsername(),
                entity.getPasswordHash(),
                entity.getFullName(),
                entity.getAvatar(),
                entity.getIsActive(),
                entity.getIsSuperAdmin(),
                entity.getLastLoginAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    public static AdminEntity toEntity(Admin admin) {
        return AdminEntity.builder()
                .id(admin.getId().getValue())
                .username(admin.getUsername())
                .passwordHash(admin.getPasswordHash())
                .fullName(admin.getFullName())
                .avatar(admin.getAvatar())
                .isActive(admin.getIsActive())
                .isSuperAdmin(admin.getIsSuperAdmin())
                .lastLoginAt(admin.getLastLoginAt())
                .createdAt(admin.getCreatedAt())
                .updatedAt(admin.getUpdatedAt())
                .version(admin.getVersion())
                .build();
    }
}
