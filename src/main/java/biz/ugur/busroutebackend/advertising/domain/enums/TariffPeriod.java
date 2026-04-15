package biz.ugur.busroutebackend.advertising.domain.enums;

import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import lombok.Getter;

import java.time.Duration;
import java.util.Arrays;

@Getter
public enum TariffPeriod {
    DAY(Duration.ofDays(1)),
    WEEK(Duration.ofDays(7)),
    MONTH(Duration.ofDays(30)),
    QUARTER(Duration.ofDays(90)),
    YEAR(Duration.ofDays(365));

    private final Duration duration;

    TariffPeriod(Duration duration) {
        this.duration = duration;
    }

    public static TariffPeriod from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AdvertisingValidationException("period", "must not be null");
        }
        return Arrays.stream(values())
                .filter(p -> p.name().equalsIgnoreCase(raw.trim()))
                .findFirst()
                .orElseThrow(() -> new AdvertisingValidationException(
                        "period", "unknown period: " + raw));
    }
}
