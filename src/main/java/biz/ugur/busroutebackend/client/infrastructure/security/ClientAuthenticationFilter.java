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
                path.startsWith("/public/") ||
                path.startsWith("/routes/") ||
                path.startsWith("/stops/") ||
                path.startsWith("/trips/") ||
                path.startsWith("/vehicles/") ||
                path.startsWith("/trip-planning/") ||
                path.startsWith("/ws/") ||
                path.startsWith("/docs") ||
                path.startsWith("/api-docs") ||
                path.startsWith("/swagger-ui") ||
                path.startsWith("/actuator/");
    }

}