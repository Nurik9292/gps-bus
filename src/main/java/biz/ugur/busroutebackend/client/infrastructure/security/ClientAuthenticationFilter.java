package biz.ugur.busroutebackend.client.infrastructure.security;

import biz.ugur.busroutebackend.shared.infrastructure.security.BaseJwtAuthenticationFilter;
import biz.ugur.busroutebackend.shared.infrastructure.security.TokenBlacklistService;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class ClientAuthenticationFilter extends BaseJwtAuthenticationFilter<ClientPrincipal> {

    public ClientAuthenticationFilter(ClientJwtTokenService clientJwtTokenService,
                                      TokenBlacklistService tokenBlacklistService) {
        super(clientJwtTokenService, tokenBlacklistService);
    }

    @Override
    protected boolean isPublicPath(String path) {
        if (!path.startsWith("/api/v1/client/") && !path.startsWith("/api/v1/mobile/")) {
            return true;
        }

        boolean isPublic = path.equals("/api/v1/client/auth/register")
                || path.equals("/api/v1/client/auth/login")
                || path.equals("/api/v1/client/auth/center-auth")
                || path.equals("/api/v1/client/auth/refresh")
                || path.equals("/api/v1/client/auth/verify")
                || path.equals("/api/v1/client/auth/resend-verification")
                || path.equals("/api/v1/client/auth/forgot-password")
                || path.equals("/api/v1/client/auth/reset-password");

        if (isPublic) {
            log.debug("Client/Mobile path is public: {}", path);
        }
        return isPublic;
    }
}