package biz.ugur.busroutebackend.integration.application.service;

import biz.ugur.busroutebackend.integration.infrastructure.persistence.entity.ApiLogEntity;
import biz.ugur.busroutebackend.integration.infrastructure.persistence.repository.R2dbcApiLogRepository;
import biz.ugur.busroutebackend.integration.infrastructure.security.ApiTokenPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiUsageLoggingService {

    private final R2dbcApiLogRepository apiLogRepository;

    public Mono<Void> logApiRequest(ServerWebExchange exchange,
                                     int responseStatus,
                                     long responseTimeMs) {

        return ReactiveSecurityContextHolder.getContext()
                .flatMap(context -> {
                    if (context.getAuthentication() == null ||
                        !(context.getAuthentication().getPrincipal() instanceof ApiTokenPrincipal principal)) {
                        return Mono.empty();
                    }

                    String endpoint = exchange.getRequest().getPath().value();
                    String httpMethod = exchange.getRequest().getMethod().name();
                    String ipAddress = extractIpAddress(exchange);
                    String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");

                    ApiLogEntity logEntity = ApiLogEntity.builder()
                            .id(UUID.randomUUID().toString())
                            .externalServiceId(principal.getExternalServiceId())
                            .endpoint(endpoint)
                            .httpMethod(httpMethod)
                            .responseStatus(responseStatus)
                            .responseTimeMs((int) responseTimeMs)
                            .ipAddress(ipAddress)
                            .userAgent(userAgent)
                            .errorMessage(null)
                            .createdAt(LocalDateTime.now())
                            .build();

                    return apiLogRepository.save(logEntity);
                })
                .onErrorResume(error -> {
                    log.error("Error logging API usage", error);
                    return Mono.empty();
                });
    }

    public Mono<Void> logFailedRequest(String externalServiceId,
                                         ServerWebExchange exchange,
                                         String errorMessage) {

        String endpoint = exchange.getRequest().getPath().value();
        String httpMethod = exchange.getRequest().getMethod().name();
        String ipAddress = extractIpAddress(exchange);
        String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");

        ApiLogEntity logEntity = ApiLogEntity.builder()
                .id(UUID.randomUUID().toString())
                .externalServiceId(externalServiceId)
                .endpoint(endpoint)
                .httpMethod(httpMethod)
                .responseStatus(401)
                .responseTimeMs(0)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .errorMessage(errorMessage)
                .createdAt(LocalDateTime.now())
                .build();

        return apiLogRepository.save(logEntity)
                .onErrorResume(error -> {
                    log.error("Error logging failed API request", error);
                    return Mono.empty();
                });
    }

    public Mono<java.util.List<ApiLogEntity>> getRecentLogs(String externalServiceId, int limit) {
        return apiLogRepository.findByExternalServiceId(externalServiceId, limit)
                .collectList();
    }

    public Mono<Long> cleanupOldLogs(int retentionDays) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        return apiLogRepository.deleteOlderThan(threshold);
    }

    private String extractIpAddress(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp;
        }

        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }

        return "unknown";
    }
}
