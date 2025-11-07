package biz.ugur.busroutebackend.shared.infrastructure.bootstrap;

import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap {

    private final AdminRepository adminRepository;

    @Value("${app.admin.default-username:admin}")
    private String defaultUsername;

    @Value("${app.admin.default-password:admin123}")
    private String defaultPassword;

    @EventListener(ApplicationReadyEvent.class)
    public void createDefaultAdminIfNotExists() {
        log.info("Checking for default admin user...");


        adminRepository.findByUsername(defaultUsername)
                .switchIfEmpty(createDefaultAdmin())
                .doOnSuccess(admin -> log.info("Default admin user ensured: {}", admin.getUsername()))
                .doOnError(error -> {
                    log.error("Failed to create default admin: {}", error.getMessage(), error);
                })
                .subscribe();
    }

    private Mono<Admin> createDefaultAdmin() {
        log.info("Creating default admin user: {}", defaultUsername);

        Admin defaultAdmin = Admin.create(
                defaultUsername,
                defaultPassword,
                "Super Administrator",
                null,  // avatar
                true,
                true);


        return adminRepository.save(defaultAdmin)
                .doOnSuccess(admin -> log.info("Default admin created successfully: {}", admin.getUsername()));
    }
}