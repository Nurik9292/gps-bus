package biz.ugur.busroutebackend.advertising.domain.valueobjects;

import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = false)
public class PlacementWindow extends ValueObject {

    private final LocalDateTime startsAt;
    private final LocalDateTime endsAt;

    private PlacementWindow(LocalDateTime startsAt, LocalDateTime endsAt) {
        if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new AdvertisingValidationException("placementWindow",
                    "endsAt must be strictly after startsAt");
        }
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public static PlacementWindow of(LocalDateTime startsAt, LocalDateTime endsAt) {
        return new PlacementWindow(startsAt, endsAt);
    }

    public static PlacementWindow unscheduled() {
        return new PlacementWindow(null, null);
    }

    public boolean isActiveAt(LocalDateTime moment) {
        if (startsAt != null && moment.isBefore(startsAt)) return false;
        if (endsAt != null && !moment.isBefore(endsAt)) return false;
        return true;
    }

    public boolean isExpiredAt(LocalDateTime moment) {
        return endsAt != null && !moment.isBefore(endsAt);
    }
}
