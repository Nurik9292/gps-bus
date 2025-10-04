package biz.ugur.busroutebackend.admin.infrastructure.security;

import biz.ugur.busroutebackend.shared.infrastructure.security.BaseJwtAuthenticationFilter;
import biz.ugur.busroutebackend.shared.infrastructure.security.TokenBlacklistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;


@Slf4j
@Component
public class AdminAuthenticationFilter extends BaseJwtAuthenticationFilter<AdminPrincipal> {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/admin/auth/login",
            "/admin/auth/refresh",
            "/public",
            "/client",
            "/mobile",
            "/routes",
            "/stops",
            "/trips",
            "/vehicles",
            "/trip-planning",
            "/ws"
    );

    public AdminAuthenticationFilter(AdminJwtTokenService adminJwtTokenService,
                                     TokenBlacklistService tokenBlacklistService) {
        super(adminJwtTokenService, tokenBlacklistService);
    }

    @Override
    protected boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith) ||
                path.startsWith("/actuator") ||
                path.startsWith("/swagger") ||
                path.startsWith("/api/v1/client") ||
                path.startsWith("/api/v1/mobile") ||
                path.startsWith("/api-docs");
    }

}
