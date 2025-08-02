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
        return  new Admin(
                username,
                password,
                fullName,
                isSuperAdmin,
                isActive
        );
    }
}
