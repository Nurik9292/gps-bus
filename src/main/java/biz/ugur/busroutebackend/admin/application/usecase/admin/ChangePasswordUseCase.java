package biz.ugur.busroutebackend.admin.application.usecase.admin;

import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.shared.domain.services.PasswordEncoder;
import biz.ugur.busroutebackend.shared.infrastructure.security.TokenBlacklistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class ChangePasswordUseCase extends BaseUseCase<Mono<ChangePasswordUseCase.Request>, Admin> {

    private final AdminRepository adminRepository;
    private final TokenBlacklistService tokenBlacklistService;
    private final PasswordEncoder passwordEncoder;

    protected ChangePasswordUseCase(CorrelationContextService correlationService,
                                    EventBus eventBus,
                                    AdminRepository adminRepository,
                                    TokenBlacklistService tokenBlacklistService,
                                    PasswordEncoder passwordEncoder) {
        super(correlationService, eventBus);
        this.adminRepository = adminRepository;
        this.tokenBlacklistService = tokenBlacklistService;
        this.passwordEncoder = passwordEncoder;
    }

    public record Request(
            AdminId adminId,
            String currentPassword,
            String newPassword,
            String currentAccessToken
    ) {}

    @Override
    protected Mono<Admin> process(Mono<Request> request) {
        return request.flatMap(this::processInternal);
    }

    private Mono<Admin> processInternal(Request request) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Processing password change for admin CorrelationID: {} - AdminId: {}",
                    correlationId, request.adminId().getValue());

            return adminRepository.findById(request.adminId())
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Admin not found")))
                    .filter(admin -> admin.checkPassword(request.currentPassword(), passwordEncoder))
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Current password is incorrect")))
                    .flatMap(admin -> {
                        String encodedNewPassword = passwordEncoder.encode(request.newPassword());
                        Admin updatedAdmin = admin.changePassword(encodedNewPassword);
                        return adminRepository.save(updatedAdmin);
                    })
                    .flatMap(admin -> {
                        return tokenBlacklistService.blacklistAccessToken(request.currentAccessToken())
                                .then(Mono.just(admin));
                    })
                    .doOnSuccess(admin -> log.info("Password changed successfully for admin: {}", admin.getUsername()))
                    .doOnError(error -> log.warn("Password change failed for admin {}: {}",
                            request.adminId().getValue(), error.getMessage()));
        });
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }
}