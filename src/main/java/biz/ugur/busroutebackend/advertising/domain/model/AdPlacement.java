package biz.ugur.busroutebackend.advertising.domain.model;

import biz.ugur.busroutebackend.advertising.domain.enums.ContentType;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementKind;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementSource;
import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.events.AdPlacementApprovedEvent;
import biz.ugur.busroutebackend.advertising.domain.events.AdPlacementCreatedEvent;
import biz.ugur.busroutebackend.advertising.domain.events.AdPlacementRejectedEvent;
import biz.ugur.busroutebackend.advertising.domain.events.AdPlacementStatusChangedEvent;
import biz.ugur.busroutebackend.advertising.domain.events.AdPlacementUpdatedEvent;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import biz.ugur.busroutebackend.advertising.domain.exceptions.PlacementStateTransitionException;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementTarget;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementWindow;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffId;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


@Builder(toBuilder = true)
@Getter
@EqualsAndHashCode(callSuper = false)
public class AdPlacement extends AggregateRoot<AdPlacement, PlacementId> {

    private final PlacementId id;
    private final BusinessId businessId;
    private final TariffId tariffId;
    private final PlacementType placementType;
    private final PlacementKind kind;
    private final PlacementStatus status;

    private final String title;
    private final String content;
    private final String imageUrl;
    private final String targetUrl;
    private final String ctaText;
    private final ContentType contentType;

    private final PlacementWindow window;

    private final List<PlacementTarget> targets;
    private final Integer displayOrder;

    private final PlacementSource source;
    private final String externalServiceId;
    private final String externalRef;

    private final String rejectionReason;
    private final LocalDateTime approvedAt;
    private final String approvedByAdminId;
    private final LocalDateTime rejectedAt;
    private final String rejectedByAdminId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    public static AdPlacement create(BusinessId businessId,
                                      TariffId tariffId,
                                      PlacementType placementType,
                                      PlacementKind kind,
                                      String title,
                                      String content,
                                      String imageUrl,
                                      String targetUrl,
                                      String ctaText,
                                      ContentType contentType,
                                      PlacementWindow window,
                                      List<PlacementTarget> targets,
                                      Integer displayOrder) {
        PlacementKind resolvedKind = kind != null ? kind : PlacementKind.COMMERCIAL;
        if (resolvedKind == PlacementKind.COMMERCIAL) {
            if (businessId == null) throw new AdvertisingValidationException("businessId", "must not be null for COMMERCIAL");
            if (tariffId == null) throw new AdvertisingValidationException("tariffId", "must not be null for COMMERCIAL");
        }
        if (placementType == null) throw new AdvertisingValidationException("placementType", "must not be null");
        if (title == null || title.trim().isEmpty()) {
            throw new AdvertisingValidationException("title", "must not be blank");
        }
        ContentType resolvedContentType = contentType != null ? contentType : ContentType.LINK;
        validateContentConsistency(resolvedContentType, content, targetUrl);

        List<PlacementTarget> resolvedTargets = immutableCopy(targets);

        AdPlacement placement = builder()
                .id(PlacementId.generate())
                .businessId(businessId)
                .tariffId(tariffId)
                .placementType(placementType)
                .kind(resolvedKind)
                .status(PlacementStatus.DRAFT)
                .title(title.trim())
                .content(trimOrNull(content))
                .imageUrl(trimOrNull(imageUrl))
                .targetUrl(trimOrNull(targetUrl))
                .ctaText(trimOrNull(ctaText))
                .contentType(resolvedContentType)
                .window(window != null ? window : PlacementWindow.unscheduled())
                .targets(resolvedTargets)
                .displayOrder(displayOrder != null ? displayOrder : 0)
                .source(PlacementSource.MANUAL)
                .version(0L)
                .build();

        placement.registerEvent(new AdPlacementCreatedEvent(
                placement.id.getValue(),
                businessId != null ? businessId.getValue() : null,
                tariffId != null ? tariffId.getValue() : null,
                placementType,
                resolvedKind));

        return placement;
    }

    public static AdPlacement createExternal(String externalServiceId,
                                            String externalRef,
                                            PlacementType placementType,
                                            String title,
                                            String content,
                                            String imageUrl,
                                            String targetUrl,
                                            String ctaText,
                                            ContentType contentType,
                                            PlacementWindow window,
                                            List<PlacementTarget> targets,
                                            Integer displayOrder) {
        if (externalServiceId == null || externalServiceId.isBlank()) {
            throw new AdvertisingValidationException("externalServiceId", "must not be blank for EXTERNAL source");
        }
        if (externalRef == null || externalRef.isBlank()) {
            throw new AdvertisingValidationException("externalRef", "must not be blank for EXTERNAL source");
        }
        validateRoutesOnly(targets);

        AdPlacement placement = create(null, null, placementType, PlacementKind.EDITORIAL,
                title, content, imageUrl, targetUrl, ctaText, contentType, window, targets, displayOrder);

        return placement.toBuilder()
                .source(PlacementSource.EXTERNAL)
                .externalServiceId(externalServiceId.trim())
                .externalRef(externalRef.trim())
                .build();
    }

    private static void validateRoutesOnly(List<PlacementTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            throw new AdvertisingValidationException("targets", "external placement must target ROUTES_LIST");
        }
        boolean onlyRoutes = targets.stream()
                .allMatch(target -> target.getTargetType() == TargetType.ROUTES_LIST);
        if (!onlyRoutes) {
            throw new AdvertisingValidationException("targets",
                    "external placement accepts only ROUTES_LIST target");
        }
    }

    public void ensureEditableByAdmin() {
        if (isExternal()) {
            throw new AdvertisingValidationException("source",
                    "external placement content is managed by the owning service; admin may only withdraw it from display");
        }
    }

    public boolean isExternal() {
        return source == PlacementSource.EXTERNAL;
    }

    public boolean isOwnedBy(String serviceId) {
        return isExternal() && externalServiceId != null && externalServiceId.equals(serviceId);
    }

    private static void validateContentConsistency(ContentType contentType, String content, String targetUrl) {
        if (contentType == ContentType.CONTENT && (content == null || content.isBlank())) {
            throw new AdvertisingValidationException("content", "required when contentType=CONTENT");
        }
        if (contentType == ContentType.LINK && (targetUrl == null || targetUrl.isBlank())) {
            throw new AdvertisingValidationException("targetUrl", "required when contentType=LINK");
        }
        if (contentType == ContentType.LINK && content != null && !content.isBlank()) {
            throw new AdvertisingValidationException("content", "must be empty for LINK type");
        }
    }

    public static AdPlacement restore(PlacementId id,
                                       BusinessId businessId,
                                       TariffId tariffId,
                                       PlacementType placementType,
                                       PlacementKind kind,
                                       PlacementStatus status,
                                       String title,
                                       String content,
                                       String imageUrl,
                                       String targetUrl,
                                       String ctaText,
                                       ContentType contentType,
                                       PlacementWindow window,
                                       List<PlacementTarget> targets,
                                       Integer displayOrder,
                                       String rejectionReason,
                                       LocalDateTime approvedAt,
                                       String approvedByAdminId,
                                       LocalDateTime rejectedAt,
                                       String rejectedByAdminId,
                                       LocalDateTime createdAt,
                                       LocalDateTime updatedAt,
                                       Long version) {
        return restore(PlacementSource.MANUAL, null, null,
                id, businessId, tariffId, placementType, kind, status,
                title, content, imageUrl, targetUrl, ctaText, contentType,
                window, targets, displayOrder, rejectionReason,
                approvedAt, approvedByAdminId, rejectedAt, rejectedByAdminId,
                createdAt, updatedAt, version);
    }

    public static AdPlacement restore(PlacementSource source,
                                      String externalServiceId,
                                      String externalRef,
                                      PlacementId id,
                                       BusinessId businessId,
                                       TariffId tariffId,
                                       PlacementType placementType,
                                       PlacementKind kind,
                                       PlacementStatus status,
                                       String title,
                                       String content,
                                       String imageUrl,
                                       String targetUrl,
                                       String ctaText,
                                       ContentType contentType,
                                       PlacementWindow window,
                                       List<PlacementTarget> targets,
                                       Integer displayOrder,
                                       String rejectionReason,
                                       LocalDateTime approvedAt,
                                       String approvedByAdminId,
                                       LocalDateTime rejectedAt,
                                       String rejectedByAdminId,
                                       LocalDateTime createdAt,
                                       LocalDateTime updatedAt,
                                       Long version) {
        return builder()
                .source(source != null ? source : PlacementSource.MANUAL)
                .externalServiceId(externalServiceId)
                .externalRef(externalRef)
                .id(id).businessId(businessId).tariffId(tariffId)
                .placementType(placementType)
                .kind(kind != null ? kind : PlacementKind.COMMERCIAL)
                .status(status)
                .title(title).content(content).imageUrl(imageUrl)
                .targetUrl(targetUrl).ctaText(ctaText)
                .contentType(contentType != null ? contentType : ContentType.LINK)
                .window(window)
                .targets(immutableCopy(targets))
                .displayOrder(displayOrder)
                .rejectionReason(rejectionReason)
                .approvedAt(approvedAt)
                .approvedByAdminId(approvedByAdminId)
                .rejectedAt(rejectedAt)
                .rejectedByAdminId(rejectedByAdminId)
                .createdAt(createdAt).updatedAt(updatedAt)
                .version(version != null ? version : 0L)
                .build();
    }

    public AdPlacement withTargets(List<PlacementTarget> targets) {
        return this.toBuilder().targets(immutableCopy(targets)).build();
    }

    public AdPlacement updateEditorialContent(String title,
                                               String content,
                                               String imageUrl,
                                               String targetUrl,
                                               String ctaText,
                                               ContentType contentType,
                                               PlacementWindow window,
                                               List<PlacementTarget> targets,
                                               Integer displayOrder) {
        if (this.kind != PlacementKind.EDITORIAL) {
            throw new IllegalStateException(
                    "updateEditorialContent allowed only for EDITORIAL kind, got " + this.kind);
        }
        if (this.status == PlacementStatus.EXPIRED || this.status == PlacementStatus.CANCELLED) {
            throw new PlacementStateTransitionException(
                    "Cannot update placement in status " + this.status);
        }
        if (title == null || title.trim().isEmpty()) {
            throw new AdvertisingValidationException("title", "must not be blank");
        }
        ContentType resolvedContentType = contentType != null ? contentType : ContentType.LINK;
        validateContentConsistency(resolvedContentType, content, targetUrl);

        String newTitle = title.trim();
        String newContent = trimOrNull(content);
        String newImageUrl = trimOrNull(imageUrl);
        String newTargetUrl = trimOrNull(targetUrl);
        String newCtaText = trimOrNull(ctaText);
        PlacementWindow newWindow = window != null ? window : PlacementWindow.unscheduled();
        List<PlacementTarget> newTargets = immutableCopy(targets);
        int newDisplayOrder = displayOrder != null ? displayOrder : 0;

        Map<String, Object> changes = new HashMap<>();
        if (!Objects.equals(this.title, newTitle)) changes.put("title", newTitle);
        if (!Objects.equals(this.content, newContent)) changes.put("content", newContent);
        if (!Objects.equals(this.imageUrl, newImageUrl)) changes.put("imageUrl", newImageUrl);
        if (!Objects.equals(this.targetUrl, newTargetUrl)) changes.put("targetUrl", newTargetUrl);
        if (!Objects.equals(this.ctaText, newCtaText)) changes.put("ctaText", newCtaText);
        if (this.contentType != resolvedContentType) {
            changes.put("contentType", resolvedContentType.name());
        }
        if (!Objects.equals(this.window, newWindow)) changes.put("window", newWindow);
        if (this.displayOrder == null || this.displayOrder != newDisplayOrder) {
            changes.put("displayOrder", newDisplayOrder);
        }

        AdPlacement updated = this.toBuilder()
                .title(newTitle)
                .content(newContent)
                .imageUrl(newImageUrl)
                .targetUrl(newTargetUrl)
                .ctaText(newCtaText)
                .contentType(resolvedContentType)
                .window(newWindow)
                .targets(newTargets)
                .displayOrder(newDisplayOrder)
                .build();

        updated.registerEvent(new AdPlacementUpdatedEvent(
                this.id.getValue(), null, changes));

        return updated;
    }

    public AdPlacement markAsPendingPayment() { return transition(PlacementStatus.PENDING_PAYMENT); }
    public AdPlacement markAsScheduled()       { return transition(PlacementStatus.SCHEDULED); }
    public AdPlacement markAsActive()          { return transition(PlacementStatus.ACTIVE); }
    public AdPlacement markAsPaused()          { return transition(PlacementStatus.PAUSED); }
    public AdPlacement markAsResumed()         { return transition(PlacementStatus.ACTIVE); }
    public AdPlacement markAsExpired()         { return transition(PlacementStatus.EXPIRED); }
    public AdPlacement cancel()                { return transition(PlacementStatus.CANCELLED); }

    public AdPlacement approve(String adminId) {
        if (adminId == null || adminId.isBlank()) {
            throw new AdvertisingValidationException("adminId", "must not be blank");
        }
        if (this.status != PlacementStatus.DRAFT) {
            throw new PlacementStateTransitionException(this.status, PlacementStatus.PENDING_PAYMENT);
        }
        AdPlacement next = this.toBuilder()
                .status(PlacementStatus.PENDING_PAYMENT)
                .approvedAt(LocalDateTime.now())
                .approvedByAdminId(adminId)
                .rejectionReason(null)
                .rejectedAt(null)
                .rejectedByAdminId(null)
                .build();
        next.registerEvent(new AdPlacementStatusChangedEvent(
                this.id.getValue(), this.status, PlacementStatus.PENDING_PAYMENT));
        next.registerEvent(new AdPlacementApprovedEvent(this.id.getValue(), adminId));
        return next;
    }

    public AdPlacement reject(String adminId, String reason) {
        if (adminId == null || adminId.isBlank()) {
            throw new AdvertisingValidationException("adminId", "must not be blank");
        }
        String trimmedReason = reason != null ? reason.trim() : null;
        if (trimmedReason == null || trimmedReason.isEmpty()) {
            throw new AdvertisingValidationException("reason", "must not be blank");
        }
        if (this.status != PlacementStatus.DRAFT) {
            throw new PlacementStateTransitionException(this.status, PlacementStatus.CANCELLED);
        }
        AdPlacement next = this.toBuilder()
                .status(PlacementStatus.CANCELLED)
                .rejectionReason(trimmedReason)
                .rejectedAt(LocalDateTime.now())
                .rejectedByAdminId(adminId)
                .build();
        next.registerEvent(new AdPlacementStatusChangedEvent(
                this.id.getValue(), this.status, PlacementStatus.CANCELLED));
        next.registerEvent(new AdPlacementRejectedEvent(
                this.id.getValue(), adminId, trimmedReason));
        return next;
    }

    private AdPlacement transition(PlacementStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new PlacementStateTransitionException(this.status, target);
        }
        AdPlacement next = this.toBuilder().status(target).build();
        next.registerEvent(new AdPlacementStatusChangedEvent(
                this.id.getValue(), this.status, target));
        return next;
    }

    private static String trimOrNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private static List<PlacementTarget> immutableCopy(List<PlacementTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(targets));
    }

    @Override public PlacementId getId() { return id; }
    @Override public LocalDateTime getCreatedAt() { return createdAt; }
    @Override public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    @Override public LocalDateTime getUpdatedAt() { return updatedAt; }
    @Override public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    @Override public Long getVersion() { return version; }
    @Override public void setVersion(Long version) { this.version = version; }
}
