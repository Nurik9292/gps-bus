package biz.ugur.busroutebackend.admin.infrastructure.security;

import biz.ugur.busroutebackend.shared.infrastructure.security.BaseJwtAuthenticationFilter;
import biz.ugur.busroutebackend.shared.infrastructure.security.TokenBlacklistService;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;


@Slf4j
public class AdminAuthenticationFilter extends BaseJwtAuthenticationFilter<AdminPrincipal> {

    private static final Set<String> PUBLIC_ADMIN_PATHS = Set.of(
            "/api/v1/admin/auth/login",
            "/api/v1/admin/auth/refresh"
    );

    public AdminAuthenticationFilter(AdminJwtTokenService adminJwtTokenService,
                                     TokenBlacklistService tokenBlacklistService) {
        super(adminJwtTokenService, tokenBlacklistService);
    }


    @Override
    protected boolean isPublicPath(String path) {
        if (!path.startsWith("/api/v1/admin/")) {
            return true;
        }

        return PUBLIC_ADMIN_PATHS.contains(path);
    }
}
