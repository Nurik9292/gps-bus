package biz.ugur.busroutebackend.admin.application.usecase;

import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutUseCase implements UseCase<LogoutUseCase.Request, Mono<Void>> {

    private final TokenBlacklistService tokenBlacklistService;

    public record Request(AdminId adminId, String accessToken) {}

    @Override
    public Mono<Void> execute(Request request) {
        log.info("Processing logout request for admin: {}", request.adminId().getValue());

        return tokenBlacklistService.blacklistAccessToken(request.accessToken())
                .doOnSuccess(v -> log.info("Logout successful for admin: {}", request.adminId().getValue()))
                .doOnError(error -> log.warn("Logout failed for admin {}: {}", request.adminId().getValue(), error.getMessage()));
    }
}