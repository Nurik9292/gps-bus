package biz.ugur.busroutebackend.advertising.domain.model;

import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.events.AdPlacementCreatedEvent;
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

/**
 * A concrete ad instance purchased by a business against a tariff.
 * Lifecycle: DRAFT → PENDING_PAYMENT → SCHEDULED → ACTIVE → EXPIRED/CANCELLED.
 *
 * <p>Payment module will trigger {@link #markAsScheduled()} once payment is captured.
 * A scheduler job will flip SCHEDULED → ACTIVE when {@link PlacementWindow#startsAt} is reached,
 * and ACTIVE → EXPIRED when {@link PlacementWindow#endsAt} passes.
 */
@Builder(toBuilder = true)
@Getter
@EqualsAndHashCode(callSuper = false)
public class AdPlacement extends AggregateRoot<AdPlacement, PlacementId> {

    private final PlacementId id;
    private final BusinessId businessId;
    private final TariffId tariffId;
    private final PlacementType placementType;
    private final PlacementStatus status;

    // Creative
    private final String title;
    private final String content;
    private final String imageUrl;
    private final String targetUrl;
    private final String ctaText;

    private final PlacementWindow window;

    // Runtime counters (updated by impression/click endpoints)
    private final Long impressionsCount;
    private final Long clicksCount;

    // Comma-separated contexts e.g. "home,trip-search,notifications"; excludes map.html.
    private final String displayContexts;
    private final Integer displayOrder;

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
                .createdAt(createdAt).updatedAt(updatedAt)
                .version(version != null ? version : 0L)
                .build();
    }

    public AdPlacement markAsPendingPayment() { return transition(PlacementStatus.PENDING_PAYMENT); }
    public AdPlacement markAsScheduled()       { return transition(PlacementStatus.SCHEDULED); }
    public AdPlacement markAsActive()          { return transition(PlacementStatus.ACTIVE); }
    public AdPlacement markAsExpired()         { return transition(PlacementStatus.EXPIRED); }
    public AdPlacement cancel()                { return transition(PlacementStatus.CANCELLED); }

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
