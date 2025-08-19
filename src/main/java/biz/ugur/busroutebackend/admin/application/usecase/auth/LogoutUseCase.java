package biz.ugur.busroutebackend.admin.application.usecase.auth;

import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.shared.infrastructure.security.TokenBlacklistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class LogoutUseCase extends BaseUseCase<Mono<LogoutUseCase.Request>, Void> {

    private final TokenBlacklistService tokenBlacklistService;


    protected LogoutUseCase(CorrelationContextService correlationService,
                            EventBus eventBus,
                            TokenBlacklistService tokenBlacklistService) {
        super(correlationService, eventBus);
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    protected Mono<Void> process(Mono<Request> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<Void> processInternal(Request req) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Processing logout request for admin CorrelationId: {} - AdminId: {}", correlationId, req.adminId().getValue());
            return tokenBlacklistService.blacklistAccessToken(req.accessToken())
                    .doOnSuccess(v -> log.info("Logout successful for admin: {}", req.adminId().getValue()))
                    .doOnError(error -> log.warn("Logout failed for admin {}: {}", req.adminId().getValue(), error.getMessage()));
        });
    }


    public record Request(AdminId adminId, String accessToken) {}

}