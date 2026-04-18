package biz.ugur.busroutebackend.advertising.domain.model;

import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.enums.TariffPeriod;
import biz.ugur.busroutebackend.advertising.domain.events.AdTariffCreatedEvent;
import biz.ugur.busroutebackend.advertising.domain.events.AdTariffToggledEvent;
import biz.ugur.busroutebackend.advertising.domain.events.AdTariffUpdatedEvent;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.Price;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffId;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffName;
import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


@Builder(toBuilder = true)
@Getter
@EqualsAndHashCode(callSuper = false)
public class AdTariff extends AggregateRoot<AdTariff, TariffId> {

    private final TariffId id;
    private final TariffName name;
    private final String description;
    private final PlacementType placementType;
    private final TariffPeriod period;
    private final Price price;

    private final Integer maxImpressions;
    private final Integer maxClicks;
    private final Integer dailyImpressionCap;

    private final Boolean isActive;
    private final Integer displayOrder;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    public static AdTariff create(TariffName name,
                                   String description,
                                   PlacementType placementType,
                                   TariffPeriod period,
                                   Price price,
                                   Integer maxImpressions,
                                   Integer maxClicks,
                                   Integer dailyImpressionCap,
                                   Integer displayOrder) {
        if (name == null) throw new AdvertisingValidationException("name", "must not be null");
        if (placementType == null) throw new AdvertisingValidationException("placementType", "must not be null");
        if (period == null) throw new AdvertisingValidationException("period", "must not be null");
        if (price == null) throw new AdvertisingValidationException("price", "must not be null");

        AdTariff tariff = builder()
                .id(TariffId.generate())
                .name(name)
                .description(trimOrNull(description))
                .placementType(placementType)
                .period(period)
                .price(price)
                .maxImpressions(nonNegative(maxImpressions, "maxImpressions"))
                .maxClicks(nonNegative(maxClicks, "maxClicks"))
                .dailyImpressionCap(nonNegative(dailyImpressionCap, "dailyImpressionCap"))
                .isActive(true)
                .displayOrder(displayOrder != null ? displayOrder : 0)
                .version(0L)
                .build();

        tariff.registerEvent(new AdTariffCreatedEvent(
                tariff.id.getValue(),
                tariff.name.getValue(),
                tariff.placementType,
                tariff.period,
                tariff.price.getAmountMinor(),
                tariff.price.getCurrency()));

        return tariff;
    }

    public static AdTariff restore(TariffId id,
                                    TariffName name,
                                    String description,
                                    PlacementType placementType,
                                    TariffPeriod period,
                                    Price price,
                                    Integer maxImpressions,
                                    Integer maxClicks,
                                    Integer dailyImpressionCap,
                                    Boolean isActive,
                                    Integer displayOrder,
                                    LocalDateTime createdAt,
                                    LocalDateTime updatedAt,
                                    Long version) {
        return builder()
                .id(id)
                .name(name)
                .description(description)
                .placementType(placementType)
                .period(period)
                .price(price)
                .maxImpressions(maxImpressions)
                .maxClicks(maxClicks)
                .dailyImpressionCap(dailyImpressionCap)
                .isActive(isActive)
                .displayOrder(displayOrder)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .version(version != null ? version : 0L)
                .build();
    }

    public AdTariff update(TariffName name,
                            String description,
                            Price price,
                            Integer maxImpressions,
                            Integer maxClicks,
                            Integer dailyImpressionCap,
                            Integer displayOrder) {
        Map<String, Object> changes = new HashMap<>();

        TariffName newName = name != null ? name : this.name;
        if (!newName.equals(this.name)) changes.put("name", newName.getValue());

        String newDescription = trimOrNull(description);
        if (!Objects.equals(newDescription, this.description)) {
            changes.put("description", newDescription);
        }

        Price newPrice = price != null ? price : this.price;
        if (!newPrice.equals(this.price)) {
            changes.put("priceAmount", newPrice.getAmountMinor());
            changes.put("currency", newPrice.getCurrency());
        }

        Integer newMaxImp = nonNegative(maxImpressions, "maxImpressions");
        if (!Objects.equals(newMaxImp, this.maxImpressions)) changes.put("maxImpressions", newMaxImp);

        Integer newMaxClicks = nonNegative(maxClicks, "maxClicks");
        if (!Objects.equals(newMaxClicks, this.maxClicks)) changes.put("maxClicks", newMaxClicks);

        Integer newDailyCap = nonNegative(dailyImpressionCap, "dailyImpressionCap");
        if (!Objects.equals(newDailyCap, this.dailyImpressionCap)) {
            changes.put("dailyImpressionCap", newDailyCap);
        }

        Integer newOrder = displayOrder != null ? displayOrder : this.displayOrder;
        if (!Objects.equals(newOrder, this.displayOrder)) changes.put("displayOrder", newOrder);

        AdTariff updated = this.toBuilder()
                .name(newName)
                .description(newDescription)
                .price(newPrice)
                .maxImpressions(newMaxImp)
                .maxClicks(newMaxClicks)
                .dailyImpressionCap(newDailyCap)
                .displayOrder(newOrder)
                .build();

        if (!changes.isEmpty()) {
            updated.registerEvent(new AdTariffUpdatedEvent(this.id.getValue(), changes));
        }
        return updated;
    }

    public AdTariff activate() {
        if (Boolean.TRUE.equals(this.isActive)) return this;
        AdTariff activated = this.toBuilder().isActive(true).build();
        activated.registerEvent(new AdTariffToggledEvent(this.id.getValue(), true));
        return activated;
    }

    public AdTariff deactivate() {
        if (Boolean.FALSE.equals(this.isActive)) return this;
        AdTariff deactivated = this.toBuilder().isActive(false).build();
        deactivated.registerEvent(new AdTariffToggledEvent(this.id.getValue(), false));
        return deactivated;
    }

    private static Integer nonNegative(Integer value, String field) {
        if (value != null && value < 0) {
            throw new AdvertisingValidationException(field, "must be non-negative");
        }
        return value;
    }

    private static String trimOrNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    @Override public TariffId getId() { return id; }
    @Override public LocalDateTime getCreatedAt() { return createdAt; }
    @Override public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    @Override public LocalDateTime getUpdatedAt() { return updatedAt; }
    @Override public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    @Override public Long getVersion() { return version; }
    @Override public void setVersion(Long version) { this.version = version; }
}
