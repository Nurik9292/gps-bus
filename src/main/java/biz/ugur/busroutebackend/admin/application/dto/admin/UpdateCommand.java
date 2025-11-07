package biz.ugur.busroutebackend.admin.application.dto.admin;

import biz.ugur.busroutebackend.admin.domain.model.Admin;

public record UpdateCommand(
        String username,
        String fullName,
        String newPassword,
        Boolean isSuperAdmin,
        Boolean isActive,
        String avatar

) {
    public Admin toDomain() {
       return Admin.create(username, newPassword, fullName, avatar, isSuperAdmin, isActive);
    }
}
