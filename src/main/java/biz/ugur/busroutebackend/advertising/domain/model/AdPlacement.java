package biz.ugur.busroutebackend.advertising.domain.model;

import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.events.AdPlacementApprovedEvent;
import biz.ugur.busroutebackend.advertising.domain.events.AdPlacementCreatedEvent;
import biz.ugur.busroutebackend.advertising.domain.events.AdPlacementRejectedEvent;
import biz.ugur.busroutebackend.advertising.domain.events.AdPlacementStatusChangedEvent;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import biz.ugur.busroutebackend.advertising.domain.exceptions.PlacementStateTransitionException;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementWindow;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffId;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;


@Builder(toBuilder = true)
@Getter
@EqualsAndHashCode(callSuper = false)
public class AdPlacement extends AggregateRoot<AdPlacement, PlacementId> {

    private final PlacementId id;
    private final BusinessId businessId;
    private final TariffId tariffId;
    private final PlacementType placementType;
    private final PlacementStatus status;

    private final String title;
    private final String content;
    private final String imageUrl;
    private final String targetUrl;
    private final String ctaText;

    private final PlacementWindow window;

    private final Long impressionsCount;
    private final Long clicksCount;

    private final String displayContexts;
    private final Integer displayOrder;

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
                                      String title,
                                      String content,
                                      String imageUrl,
                                      String targetUrl,
                                      String ctaText,
                                      PlacementWindow window,
                                      List<String> displayContexts,
                                      Integer displayOrder) {
        if (businessId == null) throw new AdvertisingValidationException("businessId", "must not be null");
        if (tariffId == null) throw new AdvertisingValidationException("tariffId", "must not be null");
        if (placementType == null) throw new AdvertisingValidationException("placementType", "must not be null");
        if (title == null || title.trim().isEmpty()) {
            throw new AdvertisingValidationException("title", "must not be blank");
        }

        AdPlacement placement = builder()
                .id(PlacementId.generate())
                .businessId(businessId)
                .tariffId(tariffId)
                .placementType(placementType)
                .status(PlacementStatus.DRAFT)
                .title(title.trim())
                .content(trimOrNull(content))
                .imageUrl(trimOrNull(imageUrl))
                .targetUrl(trimOrNull(targetUrl))
                .ctaText(trimOrNull(ctaText))
                .window(window != null ? window : PlacementWindow.unscheduled())
                .displayContexts(serializeContexts(displayContexts))
                .displayOrder(displayOrder != null ? displayOrder : 0)
                .impressionsCount(0L)
                .clicksCount(0L)
                .version(0L)
                .build();

        placement.registerEvent(new AdPlacementCreatedEvent(
                placement.id.getValue(),
                businessId.getValue(),
                tariffId.getValue(),
                placementType));

        return placement;
    }

    public static AdPlacement restore(PlacementId id,
                                       BusinessId businessId,
                                       TariffId tariffId,
                                       PlacementType placementType,
                                       PlacementStatus status,
                                       String title,
                                       String content,
                                       String imageUrl,
                                       String targetUrl,
                                       String ctaText,
                                       PlacementWindow window,
                                       Long impressionsCount,
                                       Long clicksCount,
                                       String displayContexts,
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
                .id(id).businessId(businessId).tariffId(tariffId)
                .placementType(placementType).status(status)
                .title(title).content(content).imageUrl(imageUrl)
                .targetUrl(targetUrl).ctaText(ctaText)
                .window(window)
                .impressionsCount(impressionsCount != null ? impressionsCount : 0L)
                .clicksCount(clicksCount != null ? clicksCount : 0L)
                .displayContexts(displayContexts).displayOrder(displayOrder)
                .rejectionReason(rejectionReason)
                .approvedAt(approvedAt)
                .approvedByAdminId(approvedByAdminId)
                .rejectedAt(rejectedAt)
                .rejectedByAdminId(rejectedByAdminId)
                .createdAt(createdAt).updatedAt(updatedAt)
                .version(version != null ? version : 0L)
                .build();
    }

    public AdPlacement markAsPendingPayment() { return transition(PlacementStatus.PENDING_PAYMENT); }
    public AdPlacement markAsScheduled()       { return transition(PlacementStatus.SCHEDULED); }
    public AdPlacement markAsActive()          { return transition(PlacementStatus.ACTIVE); }
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

    public AdPlacement recordImpression() {
        return this.toBuilder()
                .impressionsCount(this.impressionsCount + 1)
                .build();
    }

    public AdPlacement recordClick() {
        return this.toBuilder()
                .clicksCount(this.clicksCount + 1)
                .build();
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

    private static String serializeContexts(List<String> contexts) {
        if (contexts == null || contexts.isEmpty()) return null;
        return String.join(",", contexts.stream()
                .filter(c -> c != null && !c.isBlank())
                .map(c -> c.trim().toLowerCase())
                .filter(c -> !"map.html".equals(c))
                .toList());
    }

    private static String trimOrNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    @Override public PlacementId getId() { return id; }
    @Override public LocalDateTime getCreatedAt() { return createdAt; }
    @Override public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    @Override public LocalDateTime getUpdatedAt() { return updatedAt; }
    @Override public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    @Override public Long getVersion() { return version; }
    @Override public void setVersion(Long version) { this.version = version; }
}
