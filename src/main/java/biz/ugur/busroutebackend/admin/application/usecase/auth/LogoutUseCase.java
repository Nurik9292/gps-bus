package biz.ugur.busroutebackend.admin.application.usecase.auth;

import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import biz.ugur.busroutebackend.shared.infrastructure.security.TokenBlacklistService;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutUseCase implements UseCase<Mono<LogoutUseCase.Request>, Mono<Void>> {

    private final TokenBlacklistService tokenBlacklistService;

    @Override
    public Mono<Void> execute(Mono<Request> request) {
        return request.flatMap(req -> {
            log.info("Processing logout request for admin: {}", req.adminId().getValue());
            return tokenBlacklistService.blacklistAccessToken(req.accessToken())
                    .doOnSuccess(v -> log.info("Logout successful for admin: {}", req.adminId().getValue()))
                    .doOnError(error -> log.warn("Logout failed for admin {}: {}", req.adminId().getValue(), error.getMessage()));
        });
    }

    public record Request(AdminId adminId, String accessToken) {}

}