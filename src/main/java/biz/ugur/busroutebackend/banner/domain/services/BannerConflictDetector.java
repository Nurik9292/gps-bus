package biz.ugur.busroutebackend.banner.domain.services;

import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.banner.domain.exceptions.BannerConflictException;
import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerId;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerPeriod;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class BannerConflictDetector {

    private boolean periodsOverlap(LocalDateTime start1, LocalDateTime end1,
                                   LocalDateTime start2, LocalDateTime end2) {
        if (start1 == null || start2 == null) {
            return false;
        }
        if (end1 == null || end2 == null) {
            if (end1 == null && end2 == null) {
                return true;
            }
            if (end1 == null) {
                return !start1.isAfter(end2);
            }
            return !start2.isAfter(end1);
        }

        return !start1.isAfter(end2) && !start2.isAfter(end1);
    }

    public boolean periodsOverlap(BannerPeriod period1, BannerPeriod period2) {
        if (period1 == null || period2 == null) {
            return false;
        }

        return periodsOverlap(
                period1.getStartTime(), period1.getEndTime(),
                period2.getStartTime(), period2.getEndTime()
        );
    }

    public Optional<Banner> detectPeriodConflict(Banner banner, List<Banner> existingBanners) {
        if (banner == null || existingBanners == null || existingBanners.isEmpty()) {
            return Optional.empty();
        }

        return existingBanners.stream()
                .filter(existing -> !Objects.equals(existing.getId(), banner.getId()))
                .filter(existing -> Objects.equals(existing.getType(), banner.getType()))
                .filter(existing -> periodsOverlap(banner.getPeriod(), existing.getPeriod()))
                .findFirst();
    }

    public Optional<Banner> detectDisplayOrderConflict(Banner banner, List<Banner> existingBanners) {
        if (banner == null || existingBanners == null || existingBanners.isEmpty()) {
            return Optional.empty();
        }

        return existingBanners.stream()
                .filter(existing -> !Objects.equals(existing.getId(), banner.getId()))
                .filter(existing -> Objects.equals(existing.getType(), banner.getType()))
                .filter(existing -> Objects.equals(existing.getDisplayOrder(), banner.getDisplayOrder()))
                .filter(existing -> periodsOverlap(banner.getPeriod(), existing.getPeriod()))
                .findFirst();
    }

    public void validateNoConflicts(Banner banner, List<Banner> existingBanners) {
        if (banner == null) {
            throw new IllegalArgumentException("Banner cannot be null");
        }

        detectDisplayOrderConflict(banner, existingBanners).ifPresent(conflict -> {
            throw new BannerConflictException(
                    String.format("Display order %d conflicts with existing banner '%s' (ID: %s) of type %s during overlapping period",
                            banner.getDisplayOrder(),
                            safeTitle(conflict),
                            safeIdValue(conflict),
                            safeTypeValue(conflict))
            );
        });
    }

    private static String safeTitle(Banner banner) {
        return banner.getTitle() != null ? banner.getTitle().getValue() : "—";
    }

    private static String safeIdValue(Banner banner) {
        return banner.getId() != null ? banner.getId().getValue() : "—";
    }

    private static String safeTypeValue(Banner banner) {
        return banner.getType() != null ? banner.getType().getValue() : "—";
    }

    public List<Banner> findAllConflicts(Banner banner, List<Banner> existingBanners) {
        if (banner == null || existingBanners == null || existingBanners.isEmpty()) {
            return List.of();
        }

        return existingBanners.stream()
                .filter(existing -> !existing.getId().equals(banner.getId()))
                .filter(existing -> Objects.equals(existing.getType(), banner.getType()))
                .filter(existing -> periodsOverlap(banner.getPeriod(), existing.getPeriod()))
                .filter(existing -> Objects.equals(existing.getDisplayOrder(), banner.getDisplayOrder()))
                .collect(Collectors.toList());
    }

    public boolean hasConflict(BannerId bannerId, BannerType bannerType,
                               BannerPeriod period, Integer displayOrder,
                               List<Banner> existingBanners) {
        if (existingBanners == null || existingBanners.isEmpty()) {
            return false;
        }

        return existingBanners.stream()
                .filter(existing -> !Objects.equals(existing.getId(), bannerId))
                .filter(existing -> Objects.equals(existing.getType(), bannerType))
                .anyMatch(existing -> {
                    boolean periodOverlap = periodsOverlap(period, existing.getPeriod());
                    boolean sameDisplayOrder = Objects.equals(existing.getDisplayOrder(), displayOrder);
                    return periodOverlap && sameDisplayOrder;
                });
    }

    public Optional<Banner> detectConflictForNewBanner(BannerType bannerType,
                                                        BannerPeriod period,
                                                        Integer displayOrder,
                                                        List<Banner> existingBanners) {
        if (existingBanners == null || existingBanners.isEmpty()) {
            return Optional.empty();
        }

        return existingBanners.stream()
                .filter(existing -> Objects.equals(existing.getType(), bannerType))
                .filter(existing -> periodsOverlap(period, existing.getPeriod()))
                .filter(existing -> Objects.equals(existing.getDisplayOrder(), displayOrder))
                .findFirst();
    }
}
