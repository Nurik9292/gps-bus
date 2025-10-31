package biz.ugur.busroutebackend.banner.domain.services;

import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerPeriod;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Domain service for banner scheduling logic.
 * Determines banner activation status and scheduling information.
 *
 * Note: This is a pure domain service (POJO).
 * Configuration as Spring bean is in infrastructure layer.
 */
public class BannerSchedulingService {


    public boolean shouldBeActive(Banner banner, LocalDateTime now) {
        if (banner == null) {
            return false;
        }

        if (!banner.getIsActive()) {
            return false;
        }

        return banner.getPeriod().isActive(now);
    }


    public List<Banner> filterActive(List<Banner> banners, LocalDateTime now) {
        if (banners == null || banners.isEmpty()) {
            return List.of();
        }

        return banners.stream()
                .filter(banner -> shouldBeActive(banner, now))
                .collect(Collectors.toList());
    }


    public Long daysUntilActivation(Banner banner, LocalDateTime now) {
        if (banner == null) {
            return null;
        }

        LocalDateTime checkTime = now != null ? now : LocalDateTime.now();
        LocalDateTime startTime = banner.getPeriod().getStartTime();

        if (checkTime.isAfter(startTime) || checkTime.isEqual(startTime)) {
            return null;
        }

        return ChronoUnit.DAYS.between(checkTime, startTime);
    }


    public Long daysUntilExpiration(Banner banner, LocalDateTime now) {
        if (banner == null || banner.getPeriod().getEndTime() == null) {
            return null;
        }

        LocalDateTime checkTime = now != null ? now : LocalDateTime.now();
        LocalDateTime endTime = banner.getPeriod().getEndTime();

        if (checkTime.isAfter(endTime)) {
            return null;
        }

        return ChronoUnit.DAYS.between(checkTime, endTime);
    }


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


    public List<Banner> filterExpiringWithinDays(List<Banner> banners, int days, LocalDateTime now) {
        if (banners == null || banners.isEmpty()) {
            return List.of();
        }

        return banners.stream()
                .filter(banner -> isExpiringWithinDays(banner, days, now))
                .collect(Collectors.toList());
    }


    public boolean hasExpired(Banner banner, LocalDateTime now) {
        if (banner == null || banner.getPeriod().getEndTime() == null) {
            return false;
        }

        LocalDateTime checkTime = now != null ? now : LocalDateTime.now();
        return checkTime.isAfter(banner.getPeriod().getEndTime());
    }


    public boolean isFutureScheduled(Banner banner, LocalDateTime now) {
        if (banner == null) {
            return false;
        }

        LocalDateTime checkTime = now != null ? now : LocalDateTime.now();
        return checkTime.isBefore(banner.getPeriod().getStartTime());
    }


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


    public enum BannerSchedulingStatus {
        SCHEDULED,
        ACTIVE,
        INACTIVE,
        EXPIRED
    }
}
