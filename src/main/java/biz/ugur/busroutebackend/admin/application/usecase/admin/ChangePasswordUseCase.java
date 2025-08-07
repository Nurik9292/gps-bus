package biz.ugur.busroutebackend.admin.application.usecase.admin;

import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.shared.infrastructure.security.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChangePasswordUseCase implements UseCase<ChangePasswordUseCase.Request, Mono<Admin> > {

    private final AdminRepository adminRepository;
    private final TokenBlacklistService tokenBlacklistService;

    public record Request(
            AdminId adminId,
            String currentPassword,
            String newPassword,
            String currentAccessToken
    ) {}

    @Override
    public Mono<Admin> execute(Request request) {
        log.info("Processing password change for admin: {}", request.adminId().getValue());

        return adminRepository.findById(request.adminId())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Admin not found")))
                .filter(admin -> admin.checkPassword(request.currentPassword()))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Current password is incorrect")))
                .flatMap(admin -> {
                    // Change password
                    admin.changePassword(request.newPassword());
                    return adminRepository.save(admin);
                })
                .flatMap(admin -> {
                    return tokenBlacklistService.blacklistAccessToken(request.currentAccessToken())
                            .then(Mono.just(admin));
                })
                .doOnSuccess(admin -> log.info("Password changed successfully for admin: {}", admin.getUsername()))
                .doOnError(error -> log.warn("Password change failed for admin {}: {}", request.adminId().getValue(), error.getMessage()));
    }
}