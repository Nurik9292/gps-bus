package biz.ugur.busroutebackend.banner.domain.services;

import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.banner.domain.exceptions.BannerConflictException;
import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerId;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerPeriod;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class BannerConflictDetector {

    private boolean periodsOverlap(LocalDateTime start1, LocalDateTime end1,
                                   LocalDateTime start2, LocalDateTime end2) {
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
                .filter(existing -> !existing.getId().equals(banner.getId()))
                .filter(existing -> existing.getType().equals(banner.getType())) 
                .filter(existing -> periodsOverlap(banner.getPeriod(), existing.getPeriod()))
                .findFirst();
    }

    public Optional<Banner> detectDisplayOrderConflict(Banner banner, List<Banner> existingBanners) {
        if (banner == null || existingBanners == null || existingBanners.isEmpty()) {
            return Optional.empty();
        }

        return existingBanners.stream()
                .filter(existing -> !existing.getId().equals(banner.getId())) 
                .filter(existing -> existing.getType().equals(banner.getType()))
                .filter(existing -> existing.getDisplayOrder().equals(banner.getDisplayOrder())) 
                .filter(existing -> periodsOverlap(banner.getPeriod(), existing.getPeriod())) 
                .findFirst();
    }

    public void validateNoConflicts(Banner banner, List<Banner> existingBanners) {
        if (banner == null) {
            throw new IllegalArgumentException("Banner cannot be null");
        }

        Optional<Banner> conflict = detectDisplayOrderConflict(banner, existingBanners);
        if (conflict.isPresent()) {
            throw new BannerConflictException(
                    String.format("Display order %d conflicts with existing banner '%s' (ID: %s) of type %s during overlapping period",
                            banner.getDisplayOrder(),
                            conflict.get().getTitle().getValue(),
                            conflict.get().getId().getValue(),
                            conflict.get().getType().getValue())
            );
        }
    }

    public List<Banner> findAllConflicts(Banner banner, List<Banner> existingBanners) {
        if (banner == null || existingBanners == null || existingBanners.isEmpty()) {
            return List.of();
        }

        return existingBanners.stream()
                .filter(existing -> !existing.getId().equals(banner.getId()))
                .filter(existing -> existing.getType().equals(banner.getType()))
                .filter(existing -> periodsOverlap(banner.getPeriod(), existing.getPeriod()))
                .filter(existing -> existing.getDisplayOrder().equals(banner.getDisplayOrder()))
                .collect(Collectors.toList());
    }

    public boolean hasConflict(BannerId bannerId, BannerType bannerType,
                               BannerPeriod period, Integer displayOrder,
                               List<Banner> existingBanners) {
        if (existingBanners == null || existingBanners.isEmpty()) {
            return false;
        }

        return existingBanners.stream()
                .filter(existing -> !existing.getId().equals(bannerId))
                .filter(existing -> existing.getType().equals(bannerType))
                .anyMatch(existing -> {
                    boolean periodOverlap = periodsOverlap(period, existing.getPeriod());
                    boolean sameDisplayOrder = existing.getDisplayOrder().equals(displayOrder);
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
                .filter(existing -> existing.getType().equals(bannerType))
                .filter(existing -> periodsOverlap(period, existing.getPeriod()))
                .filter(existing -> existing.getDisplayOrder().equals(displayOrder))
                .findFirst();
    }
}
