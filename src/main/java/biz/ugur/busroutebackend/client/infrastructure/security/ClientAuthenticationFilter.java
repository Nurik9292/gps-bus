package biz.ugur.busroutebackend.client.infrastructure.security;

import biz.ugur.busroutebackend.shared.infrastructure.security.BaseJwtAuthenticationFilter;
import biz.ugur.busroutebackend.shared.infrastructure.security.TokenBlacklistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class ClientAuthenticationFilter extends BaseJwtAuthenticationFilter<ClientPrincipal> {

    public ClientAuthenticationFilter(ClientJwtTokenService clientJwtTokenService,
                                      TokenBlacklistService tokenBlacklistService) {
        super(clientJwtTokenService, tokenBlacklistService);
    }

    @Override
    protected boolean isPublicPath(String path) {
        return path.startsWith("/api/v1/client/auth/") ||
                path.startsWith("/admin/") ||
                path.startsWith("/api/v1/admin/") ||
                path.startsWith("/api/v1/public/") ||
                path.startsWith("/api/v1/routes/") ||
                path.startsWith("/api/v1/stops/") ||
                path.startsWith("/api/v1/trips/") ||
                path.startsWith("/api/v1/vehicles/") ||
                path.startsWith("/api/v1/trip-planning/") ||
                path.startsWith("/api/v1/ws/") ||
                path.startsWith("/ws/") ||
                path.startsWith("/docs") ||
                path.startsWith("/api-docs") ||
                path.startsWith("/swagger-ui") ||
                path.startsWith("/actuator/");
    }

}