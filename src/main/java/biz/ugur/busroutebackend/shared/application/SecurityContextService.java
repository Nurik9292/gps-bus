package biz.ugur.busroutebackend.shared.application;

import biz.ugur.busroutebackend.admin.infrastructure.security.AdminPrincipal;
import biz.ugur.busroutebackend.client.infrastructure.security.ClientPrincipal;
import biz.ugur.busroutebackend.integration.infrastructure.security.ApiTokenPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Optional;


@Service
@Slf4j
public class SecurityContextService {


    public Mono<AdminPrincipal> getCurrentAdmin() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .filter(principal -> principal instanceof AdminPrincipal)
                .cast(AdminPrincipal.class)
                .doOnNext(admin -> log.trace("Current admin: {}", admin.getUsername()))
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("No admin principal found in security context");
                    return Mono.empty();
                }));
    }


    public Mono<ClientPrincipal> getCurrentClient() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .filter(principal -> principal instanceof ClientPrincipal)
                .cast(ClientPrincipal.class)
                .doOnNext(client -> log.trace("Current client: {}", client.getClientId()))
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("No client principal found in security context");
                    return Mono.empty();
                }));
    }


    public Mono<ApiTokenPrincipal> getCurrentApiToken() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .filter(principal -> principal instanceof ApiTokenPrincipal)
                .cast(ApiTokenPrincipal.class)
                .doOnNext(apiToken -> log.trace("Current API token: {}", apiToken.getExternalServiceId()))
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("No API token principal found in security context");
                    return Mono.empty();
                }));
    }

    public Mono<String> getCurrentAdminId() {
        return getCurrentAdmin()
                .map(admin -> admin.getId().getValue());
    }

    public Mono<String> getCurrentClientId() {
        return getCurrentClient()
                .map(ClientPrincipal::getClientId);
    }

    public Mono<String> getCurrentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .flatMap(principal -> {
                    if (principal instanceof AdminPrincipal admin) {
                        return Mono.just("admin:" + admin.getId());
                    } else if (principal instanceof ClientPrincipal client) {
                        return Mono.just("client:" + client.getClientId());
                    } else if (principal instanceof ApiTokenPrincipal apiToken) {
                        return Mono.just("api:" + apiToken.getExternalServiceId());
                    }
                    return Mono.just("anonymous");
                })
                .defaultIfEmpty("anonymous");
    }

    public Mono<String> getCurrentUsername() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .map(principal -> {
                    if (principal instanceof AdminPrincipal admin) {
                        return admin.getUsername();
                    } else if (principal instanceof ClientPrincipal client) {
                        return client.getPhone();
                    } else if (principal instanceof ApiTokenPrincipal apiToken) {
                        return apiToken.getExternalServiceId();
                    }
                    return "anonymous";
                })
                .defaultIfEmpty("anonymous");
    }

    public Mono<Boolean> isAdmin() {
        return getCurrentAdmin()
                .map(admin -> true)
                .defaultIfEmpty(false);
    }

    public Mono<Boolean> isClient() {
        return getCurrentClient()
                .map(client -> true)
                .defaultIfEmpty(false);
    }

    public Mono<Boolean> isSuperAdmin() {
        return getCurrentAdmin()
                .map(AdminPrincipal::isSuperAdmin)
                .defaultIfEmpty(false);
    }

    public Mono<String> getAuditContext(String correlationId) {
        return Mono.zip(
                getCurrentUserId(),
                getCurrentUsername()
        ).map(tuple -> String.format(
                "user=%s, username=%s, correlationId=%s",
                tuple.getT1(),
                tuple.getT2(),
                correlationId
        ));
    }

    public Mono<Void> logAudit(String action, String resource, String correlationId) {
        return getAuditContext(correlationId)
                .doOnNext(context ->
                    log.info("[AUDIT] action={}, resource={}, {}", action, resource, context)
                )
                .then();
    }
}
