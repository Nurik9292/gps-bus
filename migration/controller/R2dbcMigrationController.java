package biz.ugur.busroutebackend.migration.controller;

import biz.ugur.busroutebackend.migration.model.MigrationResult;
import biz.ugur.busroutebackend.migration.service.R2dbcMigrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;


@RestController
@RequestMapping("/admin/migration")
@ConditionalOnProperty(name = "app.migration.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class R2dbcMigrationController {

    private final R2dbcMigrationService migrationService;

    @PostMapping("/start")
    public Mono<ResponseEntity<MigrationResult>> startMigration() {
        return migrationService.performMigration()
                .map(result -> {
                    if (result.isSuccess()) {
                        return ResponseEntity.ok(result);
                    } else {
                        return ResponseEntity.status(500).body(result);
                    }
                })
                .onErrorResume(error -> {
                    MigrationResult errorResult = new MigrationResult();
                    errorResult.setSuccess(false);
                    errorResult.setErrorMessage(error.getMessage());
                    return Mono.just(ResponseEntity.status(500).body(errorResult));
                });
    }

    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, String>>> health() {
        return Mono.just(ResponseEntity.ok(Map.of("status", "OK", "service", "R2DBC Migration")));
    }
}