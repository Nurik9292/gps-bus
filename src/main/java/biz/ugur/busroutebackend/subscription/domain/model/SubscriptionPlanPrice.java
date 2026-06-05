package biz.ugur.busroutebackend.subscription.domain.model;

import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionPeriod;
import biz.ugur.busroutebackend.subscription.domain.events.SubscriptionPriceUpdatedEvent;
import biz.ugur.busroutebackend.subscription.domain.exceptions.SubscriptionValidationException;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Builder(toBuilder = true)
@Getter
@EqualsAndHashCode(callSuper = false)
public class SubscriptionPlanPrice extends AggregateRoot<SubscriptionPlanPrice, SubscriptionPeriod> {

    private final SubscriptionPeriod period;
    private final long amountMinor;
    private final String currency;
    private final String updatedBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    public static SubscriptionPlanPrice restore(SubscriptionPeriod period,
                                                long amountMinor,
                                                String currency,
                                                String updatedBy,
                                                LocalDateTime createdAt,
                                                LocalDateTime updatedAt,
                                                Long version) {
        return builder()
                .period(period)
                .amountMinor(amountMinor)
                .currency(currency)
                .updatedBy(updatedBy)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .version(version != null ? version : 0L)
                .build();
    }

    public SubscriptionPlanPrice changeAmount(long newAmountMinor, String updatedBy) {
        if (newAmountMinor <= 0) {
            throw new SubscriptionValidationException("amountMinor", "must be positive");
        }
        Map<String, Object> changes = new HashMap<>();
        if (this.amountMinor != newAmountMinor) {
            changes.put("amountMinor", newAmountMinor);
        }
        SubscriptionPlanPrice updated = this.toBuilder()
                .amountMinor(newAmountMinor)
                .updatedBy(updatedBy)
                .build();
        updated.registerEvent(new SubscriptionPriceUpdatedEvent(this.period.name(), changes));
        return updated;
    }

    @Override public SubscriptionPeriod getId() { return period; }
    @Override public LocalDateTime getCreatedAt() { return createdAt; }
    @Override public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    @Override public LocalDateTime getUpdatedAt() { return updatedAt; }
    @Override public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    @Override public Long getVersion() { return version; }
    @Override public void setVersion(Long version) { this.version = version; }
}
