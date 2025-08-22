package biz.ugur.busroutebackend.admin.application.dto.admin;

import biz.ugur.busroutebackend.admin.domain.model.Admin;

public record CreateCommand(
        String username,
        String fullName,
        String password,
        boolean isSuperAdmin,
        boolean isActive

) {

    public Admin toDomain() {
        return Admin.create(username, fullName, password, isSuperAdmin, isActive);
    }
}
