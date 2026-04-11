package biz.ugur.busroutebackend.banner.domain.services;

import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.banner.domain.exceptions.BannerConflictException;
import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerImage;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerPeriod;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerTitle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BannerConflictDetectorTest {

    private BannerConflictDetector detector;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        detector = new BannerConflictDetector();
        now = LocalDateTime.of(2025, 10, 27, 12, 0);
    }

    private Banner createBanner(String title, BannerType type, LocalDateTime start, LocalDateTime end, int displayOrder) {
        return Banner.create(
                BannerTitle.of(title),
                type,
                BannerPeriod.between(start, end),
                BannerImage.of("test.jpg"),
                "http://test.com",
                displayOrder,
                "Test content",
                type == BannerType.POPUP ? 30 : null,
                true
        );
    }

    @Test
    void periodsOverlap_ReturnTrue_WhenPeriodsOverlap() {
        BannerPeriod period1 = BannerPeriod.between(now, now.plusDays(5));
        BannerPeriod period2 = BannerPeriod.between(now.plusDays(2), now.plusDays(7));

        assertTrue(detector.periodsOverlap(period1, period2));
    }

    @Test
    void periodsOverlap_ReturnTrue_WhenOneInsideOther() {
        BannerPeriod period1 = BannerPeriod.between(now, now.plusDays(10));
        BannerPeriod period2 = BannerPeriod.between(now.plusDays(2), now.plusDays(5));

        assertTrue(detector.periodsOverlap(period1, period2));
    }

    @Test
    void periodsOverlap_ReturnFalse_WhenPeriodsDoNotOverlap() {
        BannerPeriod period1 = BannerPeriod.between(now, now.plusDays(5));
        BannerPeriod period2 = BannerPeriod.between(now.plusDays(6), now.plusDays(10));

        assertFalse(detector.periodsOverlap(period1, period2));
    }

    @Test
    void periodsOverlap_ReturnTrue_WhenBothHaveNoEnd() {
        BannerPeriod period1 = BannerPeriod.between(now, null);
        BannerPeriod period2 = BannerPeriod.between(now.plusDays(5), null);

        assertTrue(detector.periodsOverlap(period1, period2));
    }

    @Test
    void periodsOverlap_ReturnTrue_WhenOneHasNoEnd() {
        BannerPeriod period1 = BannerPeriod.between(now, null);
        BannerPeriod period2 = BannerPeriod.between(now.plusDays(5), now.plusDays(10));

        assertTrue(detector.periodsOverlap(period1, period2));
    }

    @Test
    void periodsOverlap_ReturnFalse_WhenOneHasNoEnd_AndOtherEndsBefore() {
        BannerPeriod period1 = BannerPeriod.between(now.plusDays(10), null);
        BannerPeriod period2 = BannerPeriod.between(now, now.plusDays(5));

        assertFalse(detector.periodsOverlap(period1, period2));
    }

    @Test
    void periodsOverlap_ReturnFalse_WhenEitherIsNull() {
        BannerPeriod period1 = BannerPeriod.between(now, now.plusDays(5));

        assertFalse(detector.periodsOverlap(period1, null));
        assertFalse(detector.periodsOverlap(null, period1));
        assertFalse(detector.periodsOverlap(null, null));
    }

    @Test
    void detectPeriodConflict_FindsConflict_WhenSameTypeAndOverlappingPeriod() {
        Banner banner1 = createBanner("Banner 1", BannerType.MAIN, now, now.plusDays(5), 1);
        Banner banner2 = createBanner("Banner 2", BannerType.MAIN, now.plusDays(2), now.plusDays(7), 2);

        Optional<Banner> conflict = detector.detectPeriodConflict(banner2, List.of(banner1));

        assertTrue(conflict.isPresent());
        assertEquals(banner1.getId(), conflict.get().getId());
    }

    @Test
    void detectPeriodConflict_NoConflict_WhenDifferentType() {
        Banner banner1 = createBanner("Banner 1", BannerType.MAIN, now, now.plusDays(5), 1);
        Banner banner2 = createBanner("Banner 2", BannerType.ROUTES, now.plusDays(2), now.plusDays(7), 1);

        Optional<Banner> conflict = detector.detectPeriodConflict(banner2, List.of(banner1));

        assertFalse(conflict.isPresent());
    }

    @Test
    void detectPeriodConflict_NoConflict_WhenPeriodsDoNotOverlap() {
        Banner banner1 = createBanner("Banner 1", BannerType.MAIN, now, now.plusDays(5), 1);
        Banner banner2 = createBanner("Banner 2", BannerType.MAIN, now.plusDays(6), now.plusDays(10), 1);

        Optional<Banner> conflict = detector.detectPeriodConflict(banner2, List.of(banner1));

        assertFalse(conflict.isPresent());
    }

    @Test
    void detectPeriodConflict_ExcludesSelf() {
        Banner banner1 = createBanner("Banner 1", BannerType.MAIN, now, now.plusDays(5), 1);

        Optional<Banner> conflict = detector.detectPeriodConflict(banner1, List.of(banner1));

        assertFalse(conflict.isPresent());
    }

    @Test
    void detectDisplayOrderConflict_FindsConflict_WhenSameDisplayOrderAndOverlappingPeriod() {
        Banner banner1 = createBanner("Banner 1", BannerType.MAIN, now, now.plusDays(5), 1);
        Banner banner2 = createBanner("Banner 2", BannerType.MAIN, now.plusDays(2), now.plusDays(7), 1);

        Optional<Banner> conflict = detector.detectDisplayOrderConflict(banner2, List.of(banner1));

        assertTrue(conflict.isPresent());
        assertEquals(banner1.getId(), conflict.get().getId());
    }

    @Test
    void detectDisplayOrderConflict_NoConflict_WhenDifferentDisplayOrder() {
        Banner banner1 = createBanner("Banner 1", BannerType.MAIN, now, now.plusDays(5), 1);
        Banner banner2 = createBanner("Banner 2", BannerType.MAIN, now.plusDays(2), now.plusDays(7), 2);

        Optional<Banner> conflict = detector.detectDisplayOrderConflict(banner2, List.of(banner1));

        assertFalse(conflict.isPresent());
    }

    @Test
    void detectDisplayOrderConflict_NoConflict_WhenPeriodsDoNotOverlap() {
        Banner banner1 = createBanner("Banner 1", BannerType.MAIN, now, now.plusDays(5), 1);
        Banner banner2 = createBanner("Banner 2", BannerType.MAIN, now.plusDays(6), now.plusDays(10), 1);

        Optional<Banner> conflict = detector.detectDisplayOrderConflict(banner2, List.of(banner1));

        assertFalse(conflict.isPresent());
    }

    @Test
    void validateNoConflicts_DoesNotThrow_WhenOnlyPeriodOverlaps_DifferentDisplayOrder() {
        Banner banner1 = createBanner("Banner 1", BannerType.MAIN, now, now.plusDays(5), 1);
        Banner banner2 = createBanner("Banner 2", BannerType.MAIN, now.plusDays(2), now.plusDays(7), 2);

        assertDoesNotThrow(() ->
                detector.validateNoConflicts(banner2, List.of(banner1))
        );
    }

    @Test
    void validateNoConflicts_ThrowsException_WhenSameType_SameDisplayOrder_OverlappingPeriod() {
        Banner banner1 = createBanner("Banner 1", BannerType.MAIN, now, now.plusDays(5), 1);
        Banner banner2 = createBanner("Banner 2", BannerType.MAIN, now.plusDays(2), now.plusDays(7), 1);

        BannerConflictException exception = assertThrows(BannerConflictException.class, () ->
                detector.validateNoConflicts(banner2, List.of(banner1))
        );

        assertTrue(exception.getMessage().contains("Display order"));
    }

    @Test
    void validateNoConflicts_DoesNotThrow_WhenNoConflicts() {
        Banner banner1 = createBanner("Banner 1", BannerType.MAIN, now, now.plusDays(5), 1);
        Banner banner2 = createBanner("Banner 2", BannerType.ROUTES, now.plusDays(6), now.plusDays(10), 2);

        assertDoesNotThrow(() ->
                detector.validateNoConflicts(banner2, List.of(banner1))
        );
    }

    @Test
    void validateNoConflicts_ThrowsException_WhenBannerIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                detector.validateNoConflicts(null, List.of())
        );
    }

    @Test
    void findAllConflicts_ReturnsAllConflictingBanners() {
        Banner banner1 = createBanner("Banner 1", BannerType.MAIN, now, now.plusDays(5), 1);
        Banner banner2 = createBanner("Banner 2", BannerType.MAIN, now.plusDays(2), now.plusDays(7), 2);
        Banner banner3 = createBanner("Banner 3", BannerType.MAIN, now.plusDays(4), now.plusDays(9), 3);
        Banner newBanner = createBanner("New Banner", BannerType.MAIN, now.plusDays(3), now.plusDays(8), 2);

        List<Banner> conflicts = detector.findAllConflicts(newBanner, List.of(banner1, banner2, banner3));

        assertEquals(1, conflicts.size());
        assertTrue(conflicts.contains(banner2));
    }

    @Test
    void findAllConflicts_ReturnsEmpty_WhenNoConflicts() {
        Banner banner1 = createBanner("Banner 1", BannerType.MAIN, now, now.plusDays(5), 1);
        Banner newBanner = createBanner("New Banner", BannerType.ROUTES, now.plusDays(6), now.plusDays(10), 2);

        List<Banner> conflicts = detector.findAllConflicts(newBanner, List.of(banner1));

        assertTrue(conflicts.isEmpty());
    }

    @Test
    void hasConflict_ReturnsTrue_WhenConflictExists() {
        Banner existing = createBanner("Existing", BannerType.MAIN, now, now.plusDays(5), 1);
        Banner newBanner = createBanner("New", BannerType.MAIN, now.plusDays(2), now.plusDays(7), 1);

        boolean hasConflict = detector.hasConflict(
                newBanner.getId(),
                newBanner.getType(),
                newBanner.getPeriod(),
                newBanner.getDisplayOrder(),
                List.of(existing)
        );

        assertTrue(hasConflict);
    }

    @Test
    void hasConflict_ReturnsFalse_WhenNoConflict() {
        Banner existing = createBanner("Existing", BannerType.MAIN, now, now.plusDays(5), 1);
        Banner newBanner = createBanner("New", BannerType.MAIN, now.plusDays(6), now.plusDays(10), 2);

        boolean hasConflict = detector.hasConflict(
                newBanner.getId(),
                newBanner.getType(),
                newBanner.getPeriod(),
                newBanner.getDisplayOrder(),
                List.of(existing)
        );

        assertFalse(hasConflict);
    }

    @Test
    void detectConflictForNewBanner_FindsConflict() {
        Banner existing = createBanner("Existing", BannerType.MAIN, now, now.plusDays(5), 1);

        Optional<Banner> conflict = detector.detectConflictForNewBanner(
                BannerType.MAIN,
                BannerPeriod.between(now.plusDays(2), now.plusDays(7)),
                1,
                List.of(existing)
        );

        assertTrue(conflict.isPresent());
        assertEquals(existing.getId(), conflict.get().getId());
    }

    @Test
    void detectConflictForNewBanner_NoConflict_WhenDifferentType() {
        Banner existing = createBanner("Existing", BannerType.MAIN, now, now.plusDays(5), 1);

        Optional<Banner> conflict = detector.detectConflictForNewBanner(
                BannerType.ROUTES,
                BannerPeriod.between(now.plusDays(2), now.plusDays(7)),
                1,
                List.of(existing)
        );

        assertFalse(conflict.isPresent());
    }

    @Test
    void detectConflictForNewBanner_NoConflict_WhenDifferentDisplayOrder() {
        Banner existing = createBanner("Existing", BannerType.MAIN, now, now.plusDays(5), 1);

        Optional<Banner> conflict = detector.detectConflictForNewBanner(
                BannerType.MAIN,
                BannerPeriod.between(now.plusDays(2), now.plusDays(7)),
                2,
                List.of(existing)
        );

        assertFalse(conflict.isPresent());
    }

    @Test
    void detectConflictForNewBanner_ReturnsEmpty_WhenExistingBannersIsNull() {
        Optional<Banner> conflict = detector.detectConflictForNewBanner(
                BannerType.MAIN,
                BannerPeriod.between(now, now.plusDays(5)),
                1,
                null
        );

        assertFalse(conflict.isPresent());
    }
}
