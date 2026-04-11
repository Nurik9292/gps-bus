package biz.ugur.busroutebackend.integration.domain.model;

import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.integration.domain.events.*;
import biz.ugur.busroutebackend.integration.domain.exceptions.ClientManagementNotAllowedException;
import biz.ugur.busroutebackend.integration.domain.exceptions.ExternalServiceBlockedException;
import biz.ugur.busroutebackend.integration.domain.exceptions.UnauthorizedEndpointException;
import biz.ugur.busroutebackend.integration.domain.valueobjects.ApiToken;
import biz.ugur.busroutebackend.integration.domain.valueobjects.ExternalServiceId;
import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;


@Builder(toBuilder = true)
@Getter
@ToString
@EqualsAndHashCode(callSuper = false)
public class ExternalService extends AggregateRoot<ExternalService, ExternalServiceId> {

    private final ExternalServiceId id;
    private final String name;
    private final String description;
    private final ApiToken apiToken;
    private final Boolean isActive;
    private final List<String> allowedEndpoints;
    private final Integer rateLimitPerMinute;
    private final Boolean canManageClients;
    private final LocalDateTime lastUsedAt;
    private final AdminId createdByAdminId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;


    public static ExternalService create(String name,
                                          String description,
                                          AdminId createdByAdminId,
                                          List<String> allowedEndpoints,
                                          Integer rateLimitPerMinute,
                                          Boolean canManageClients) {
        validateName(name);
        validateRateLimit(rateLimitPerMinute);

        ApiToken token = ApiToken.generate();
        ExternalServiceId id = ExternalServiceId.generate();

        ExternalService service = ExternalService.builder()
                .id(id)
                .name(name.trim())
                .description(description != null ? description.trim() : null)
                .apiToken(token)
                .isActive(true)
                .allowedEndpoints(allowedEndpoints != null ? new ArrayList<>(allowedEndpoints) : null)
                .rateLimitPerMinute(rateLimitPerMinute)
                .canManageClients(canManageClients != null ? canManageClients : false)
                .lastUsedAt(null)
                .createdByAdminId(createdByAdminId)
                .build();

        service.registerEvent(new ExternalServiceCreatedEvent(
                id.getValue(),
                name,
                token.getMaskedValue(),
                createdByAdminId.getValue()
        ));

        return service;
    }


    public static ExternalService fromDatabase(ExternalServiceId id,
                                                String name,
                                                String description,
                                                ApiToken apiToken,
                                                Boolean isActive,
                                                List<String> allowedEndpoints,
                                                Integer rateLimitPerMinute,
                                                Boolean canManageClients,
                                                LocalDateTime lastUsedAt,
                                                AdminId createdByAdminId,
                                                LocalDateTime createdAt,
                                                LocalDateTime updatedAt,
                                                Long version) {
        ExternalService service = ExternalService.builder()
                .id(id)
                .name(name)
                .description(description)
                .apiToken(apiToken)
                .isActive(isActive)
                .allowedEndpoints(allowedEndpoints != null ? new ArrayList<>(allowedEndpoints) : null)
                .rateLimitPerMinute(rateLimitPerMinute)
                .canManageClients(canManageClients != null ? canManageClients : false)
                .lastUsedAt(lastUsedAt)
                .createdByAdminId(createdByAdminId)
                .build();

        service.createdAt = createdAt;
        service.updatedAt = updatedAt;
        service.version = version;

        return service;
    }


    public ExternalService update(String name,
                                   String description,
                                   List<String> allowedEndpoints,
                                   Integer rateLimitPerMinute) {
        validateName(name);
        validateRateLimit(rateLimitPerMinute);

        ExternalService updated = this.toBuilder()
                .name(name.trim())
                .description(description != null ? description.trim() : null)
                .allowedEndpoints(allowedEndpoints != null ? new ArrayList<>(allowedEndpoints) : null)
                .rateLimitPerMinute(rateLimitPerMinute)
                .build();

        updated.registerEvent(new ExternalServiceUpdatedEvent(
                this.id.getValue(),
                name,
                description,
                allowedEndpoints,
                rateLimitPerMinute
        ));

        return updated;
    }


    public ExternalService block(AdminId blockedByAdminId, String reason) {
        if (Boolean.FALSE.equals(this.isActive)) {
            return this;
        }

        ExternalService blocked = this.toBuilder()
                .isActive(false)
                .build();

        blocked.registerEvent(new ExternalServiceBlockedEvent(
                this.id.getValue(),
                this.name,
                blockedByAdminId.getValue(),
                reason
        ));

        return blocked;
    }


    public ExternalService unblock(AdminId unblockedByAdminId) {
        if (Boolean.TRUE.equals(this.isActive)) {
            return this;
        }

        ExternalService unblocked = this.toBuilder()
                .isActive(true)
                .build();

        unblocked.registerEvent(new ExternalServiceUnblockedEvent(
                this.id.getValue(),
                this.name,
                unblockedByAdminId.getValue()
        ));

        return unblocked;
    }

    public ExternalService recordUsage() {
        return this.toBuilder()
                .lastUsedAt(LocalDateTime.now())
                .build();
    }


    public boolean canManageClients() {
        return Boolean.TRUE.equals(canManageClients) && Boolean.TRUE.equals(isActive);
    }


    public void validateClientManagement() {
        if (!Boolean.TRUE.equals(isActive)) {
            throw ExternalServiceBlockedException.byId(this.id);
        }
        if (!Boolean.TRUE.equals(canManageClients)) {
            throw new ClientManagementNotAllowedException(this.id.getValue());
        }
    }

    public boolean isEndpointAllowed(String endpoint) {
        if (allowedEndpoints == null || allowedEndpoints.isEmpty()) {
            return true; 
        }

        return allowedEndpoints.stream()
                .anyMatch(pattern -> matchesPattern(endpoint, pattern));
    }

    public void validateEndpointAccess(String endpoint) {
        if (!isEndpointAllowed(endpoint)) {
            throw new UnauthorizedEndpointException(this.name, endpoint);
        }
    }

    public List<String> getAllowedEndpoints() {
        return allowedEndpoints != null ?
            Collections.unmodifiableList(allowedEndpoints) : null;
    }

    @Override
    public ExternalServiceId getId() {
        return id;
    }

    @Override
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public Long getVersion() {
        return version;
    }

    @Override
    public void setVersion(Long version) {
        this.version = version;
    }


    private static void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("External service name cannot be null or empty");
        }
        if (name.trim().length() < 3) {
            throw new IllegalArgumentException("External service name must be at least 3 characters");
        }
        if (name.trim().length() > 255) {
            throw new IllegalArgumentException("External service name cannot exceed 255 characters");
        }
    }

    private static void validateRateLimit(Integer rateLimitPerMinute) {
        if (rateLimitPerMinute != null && rateLimitPerMinute <= 0) {
            throw new IllegalArgumentException("Rate limit must be positive");
        }
    }

    private boolean matchesPattern(String endpoint, String pattern) {
        if (pattern.equals(endpoint)) {
            return true;
        }

        String regex = pattern
                .replace("**", ".*")
                .replace("*", "[^/]*")
                .replace("?", ".");

        return Pattern.compile("^" + regex + "$").matcher(endpoint).matches();
    }
}
