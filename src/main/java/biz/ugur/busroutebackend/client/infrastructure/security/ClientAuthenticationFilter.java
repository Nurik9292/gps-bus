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

        boolean isPublic = path.startsWith("/api/v1/client/auth/") ||
                           path.startsWith("/api/v1/mobile/");

        if (isPublic) {
            log.debug("Client/Mobile path is public: {}", path);
        }
        return isPublic;
    }
}