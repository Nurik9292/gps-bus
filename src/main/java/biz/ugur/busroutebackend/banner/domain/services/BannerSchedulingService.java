package biz.ugur.busroutebackend.banner.domain.services;

import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerPeriod;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Domain service responsible for banner scheduling logic.
 *
 * This service encapsulates business rules related to:
 * - Determining banner active status based on period
 * - Filtering banners by their schedule
 * - Calculating time until activation/deactivation
 * - Identifying expiring banners
 *
 * This is a domain service because the logic involves multiple entities
 * and doesn't naturally belong to a single aggregate.
 */
@Service
public class BannerSchedulingService {

    /**
     * Checks if a banner should be active at the specified time.
     * A banner is active if:
     * 1. The isActive flag is true
     * 2. The current time is within the banner's period
     *
     * @param banner The banner to check
     * @param now The time to check against (null = current time)
     * @return true if the banner should be active, false otherwise
     */
    public boolean shouldBeActive(Banner banner, LocalDateTime now) {
        if (banner == null) {
            return false;
        }

        if (!banner.getIsActive()) {
            return false;
        }

        return banner.getPeriod().isActive(now);
    }

    /**
     * Filters a list of banners to only include those that should be active at the given time.
     *
     * @param banners List of banners to filter
     * @param now The time to check against (null = current time)
     * @return List of banners that should be active
     */
    public List<Banner> filterActive(List<Banner> banners, LocalDateTime now) {
        if (banners == null || banners.isEmpty()) {
            return List.of();
        }

        return banners.stream()
                .filter(banner -> shouldBeActive(banner, now))
                .collect(Collectors.toList());
    }

    /**
     * Calculates the number of days until a banner becomes active.
     *
     * @param banner The banner to check
     * @param now The current time (null = current time)
     * @return Number of days until activation, or null if already active or past end date
     */
    public Long daysUntilActivation(Banner banner, LocalDateTime now) {
        if (banner == null) {
            return null;
        }

        LocalDateTime checkTime = now != null ? now : LocalDateTime.now();
        LocalDateTime startTime = banner.getPeriod().getStartTime();

        if (checkTime.isAfter(startTime) || checkTime.isEqual(startTime)) {
            return null; // Already active or past activation time
        }

        return ChronoUnit.DAYS.between(checkTime, startTime);
    }

    /**
     * Calculates the number of days until a banner expires.
     *
     * @param banner The banner to check
     * @param now The current time (null = current time)
     * @return Number of days until expiration, or null if no end date or already expired
     */
    public Long daysUntilExpiration(Banner banner, LocalDateTime now) {
        if (banner == null || banner.getPeriod().getEndTime() == null) {
            return null; // No expiration date
        }

        LocalDateTime checkTime = now != null ? now : LocalDateTime.now();
        LocalDateTime endTime = banner.getPeriod().getEndTime();

        if (checkTime.isAfter(endTime)) {
            return null; // Already expired
        }

        return ChronoUnit.DAYS.between(checkTime, endTime);
    }

    /**
     * Checks if a banner is expiring within the specified number of days.
     *
     * @param banner The banner to check
     * @param days Number of days to check
     * @param now The current time (null = current time)
     * @return true if the banner will expire within the specified days
     */
    public boolean isExpiringWithinDays(Banner banner, int days, LocalDateTime now) {
        if (days < 0) {
            throw new IllegalArgumentException("Days must be non-negative");
        }

        Long daysUntilExp = daysUntilExpiration(banner, now);
        if (daysUntilExp == null) {
            return false;
        }

        return daysUntilExp >= 0 && daysUntilExp <= days;
    }

    /**
     * Filters banners that are expiring within the specified number of days.
     *
     * @param banners List of banners to filter
     * @param days Number of days to check
     * @param now The current time (null = current time)
     * @return List of banners expiring within the specified days
     */
    public List<Banner> filterExpiringWithinDays(List<Banner> banners, int days, LocalDateTime now) {
        if (banners == null || banners.isEmpty()) {
            return List.of();
        }

        return banners.stream()
                .filter(banner -> isExpiringWithinDays(banner, days, now))
                .collect(Collectors.toList());
    }

    /**
     * Checks if a banner has already expired.
     *
     * @param banner The banner to check
     * @param now The current time (null = current time)
     * @return true if the banner has expired
     */
    public boolean hasExpired(Banner banner, LocalDateTime now) {
        if (banner == null || banner.getPeriod().getEndTime() == null) {
            return false; // No end date means never expires
        }

        LocalDateTime checkTime = now != null ? now : LocalDateTime.now();
        return checkTime.isAfter(banner.getPeriod().getEndTime());
    }

    /**
     * Checks if a banner is scheduled for the future (not yet active).
     *
     * @param banner The banner to check
     * @param now The current time (null = current time)
     * @return true if the banner's start time is in the future
     */
    public boolean isFutureScheduled(Banner banner, LocalDateTime now) {
        if (banner == null) {
            return false;
        }

        LocalDateTime checkTime = now != null ? now : LocalDateTime.now();
        return checkTime.isBefore(banner.getPeriod().getStartTime());
    }

    /**
     * Determines the scheduling status of a banner.
     *
     * @param banner The banner to check
     * @param now The current time (null = current time)
     * @return The scheduling status
     */
    public BannerSchedulingStatus getSchedulingStatus(Banner banner, LocalDateTime now) {
        if (banner == null) {
            throw new IllegalArgumentException("Banner cannot be null");
        }

        if (isFutureScheduled(banner, now)) {
            return BannerSchedulingStatus.SCHEDULED;
        }

        if (hasExpired(banner, now)) {
            return BannerSchedulingStatus.EXPIRED;
        }

        if (shouldBeActive(banner, now)) {
            return BannerSchedulingStatus.ACTIVE;
        }

        return BannerSchedulingStatus.INACTIVE;
    }

    /**
     * Enum representing the scheduling status of a banner.
     */
    public enum BannerSchedulingStatus {
        /** Banner is scheduled for the future */
        SCHEDULED,
        /** Banner is currently active */
        ACTIVE,
        /** Banner is within its period but deactivated */
        INACTIVE,
        /** Banner period has ended */
        EXPIRED
    }
}
