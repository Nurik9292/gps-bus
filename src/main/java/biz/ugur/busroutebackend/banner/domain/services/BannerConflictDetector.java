package biz.ugur.busroutebackend.banner.domain.services;

import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.banner.domain.exceptions.BannerConflictException;
import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerId;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerPeriod;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Domain service responsible for detecting conflicts between banners.
 *
 * This service encapsulates business rules related to:
 * - Detecting overlapping banner periods for the same type
 * - Checking for duplicate display orders within the same period and type
 * - Validating banner constraints before creation or modification
 *
 * This is a domain service because the logic involves comparing multiple aggregates
 * and enforcing cross-aggregate invariants.
 */
@Service
public class BannerConflictDetector {

    /**
     * Checks if two time periods overlap.
     *
     * @param start1 Start time of first period
     * @param end1 End time of first period (null = no end)
     * @param start2 Start time of second period
     * @param end2 End time of second period (null = no end)
     * @return true if periods overlap
     */
    private boolean periodsOverlap(LocalDateTime start1, LocalDateTime end1,
                                   LocalDateTime start2, LocalDateTime end2) {
        // If either period has no end, check if starts overlap
        if (end1 == null || end2 == null) {
            // Both have no end - they overlap if either starts during or before the other
            if (end1 == null && end2 == null) {
                return true;
            }
            // First has no end - overlaps if second starts before first ends
            if (end1 == null) {
                return !start1.isAfter(end2);
            }
            // Second has no end - overlaps if first starts before second ends
            return !start2.isAfter(end1);
        }

        // Both have end times - check for any overlap
        // Period 1: [start1, end1], Period 2: [start2, end2]
        // They overlap if start1 <= end2 AND start2 <= end1
        return !start1.isAfter(end2) && !start2.isAfter(end1);
    }

    /**
     * Checks if two banner periods overlap.
     *
     * @param period1 First banner period
     * @param period2 Second banner period
     * @return true if periods overlap
     */
    public boolean periodsOverlap(BannerPeriod period1, BannerPeriod period2) {
        if (period1 == null || period2 == null) {
            return false;
        }

        return periodsOverlap(
                period1.getStartTime(), period1.getEndTime(),
                period2.getStartTime(), period2.getEndTime()
        );
    }

    /**
     * Detects if a banner has a period conflict with any existing banners of the same type.
     * NOTE: This method is provided for analysis purposes. In practice, overlapping periods
     * are only a problem if display orders are also the same.
     *
     * @param banner The banner to check
     * @param existingBanners List of existing banners to check against
     * @return Optional containing the conflicting banner, or empty if no conflict
     */
    public Optional<Banner> detectPeriodConflict(Banner banner, List<Banner> existingBanners) {
        if (banner == null || existingBanners == null || existingBanners.isEmpty()) {
            return Optional.empty();
        }

        return existingBanners.stream()
                .filter(existing -> !existing.getId().equals(banner.getId())) // Exclude self
                .filter(existing -> existing.getType().equals(banner.getType())) // Same type only
                .filter(existing -> periodsOverlap(banner.getPeriod(), existing.getPeriod()))
                .findFirst();
    }

    /**
     * Detects if a banner has a conflict with any existing banners.
     * A conflict occurs when banners have the same type, overlapping periods, AND the same display order.
     *
     * @param banner The banner to check
     * @param existingBanners List of existing banners to check against
     * @return Optional containing the conflicting banner, or empty if no conflict
     */
    public Optional<Banner> detectDisplayOrderConflict(Banner banner, List<Banner> existingBanners) {
        if (banner == null || existingBanners == null || existingBanners.isEmpty()) {
            return Optional.empty();
        }

        return existingBanners.stream()
                .filter(existing -> !existing.getId().equals(banner.getId())) // Exclude self
                .filter(existing -> existing.getType().equals(banner.getType())) // Same type only
                .filter(existing -> existing.getDisplayOrder().equals(banner.getDisplayOrder())) // Same display order
                .filter(existing -> periodsOverlap(banner.getPeriod(), existing.getPeriod())) // Overlapping period
                .findFirst();
    }

    /**
     * Validates a banner for any conflicts and throws an exception if found.
     * A conflict occurs when a banner has the same type, overlapping period, AND same display order
     * as an existing banner.
     *
     * @param banner The banner to validate
     * @param existingBanners List of existing banners to check against
     * @throws BannerConflictException if any conflict is detected
     */
    public void validateNoConflicts(Banner banner, List<Banner> existingBanners) {
        if (banner == null) {
            throw new IllegalArgumentException("Banner cannot be null");
        }

        // The only real conflict is: same type + overlapping period + same display order
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

    /**
     * Finds all banners that would conflict with the given banner.
     * A conflict occurs when banners have the same type, overlapping period, AND same display order.
     *
     * @param banner The banner to check
     * @param existingBanners List of existing banners to check against
     * @return List of conflicting banners
     */
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

    /**
     * Checks if a specific banner conflicts with any banners in the list.
     *
     * @param bannerId The ID of the banner to check
     * @param bannerType The type of the banner
     * @param period The period of the banner
     * @param displayOrder The display order of the banner
     * @param existingBanners List of existing banners to check against
     * @return true if there is a conflict
     */
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

    /**
     * Detects conflicts for a new banner (without an ID yet).
     *
     * @param bannerType The type of the new banner
     * @param period The period of the new banner
     * @param displayOrder The display order of the new banner
     * @param existingBanners List of existing banners to check against
     * @return Optional containing the first conflicting banner, or empty if no conflict
     */
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
