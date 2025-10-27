package biz.ugur.busroutebackend.banner.domain.services;

import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.banner.domain.model.Banner;
import biz.ugur.busroutebackend.banner.domain.services.BannerSchedulingService.BannerSchedulingStatus;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerImage;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerPeriod;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerTitle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BannerSchedulingServiceTest {

    private BannerSchedulingService service;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        service = new BannerSchedulingService();
        now = LocalDateTime.of(2025, 10, 27, 12, 0);
    }

    private Banner createTestBanner(LocalDateTime start, LocalDateTime end, boolean isActive) {
        return Banner.create(
                BannerTitle.of("Test Banner"),
                BannerType.MAIN,
                BannerPeriod.between(start, end),
                BannerImage.of("test.jpg"),
                "http://test.com",
                1,
                "Test content"
        );
    }

    @Test
    void shouldBeActive_WhenBannerIsActiveAndWithinPeriod() {
        Banner banner = createTestBanner(
                now.minusDays(1),
                now.plusDays(1),
                true
        );

        assertTrue(service.shouldBeActive(banner, now));
    }

    @Test
    void shouldNotBeActive_WhenBannerIsDeactivated() {
        Banner banner = createTestBanner(
                now.minusDays(1),
                now.plusDays(1),
                true
        );
        Banner deactivatedBanner = banner.deactivate();

        assertFalse(service.shouldBeActive(deactivatedBanner, now));
    }

    @Test
    void shouldNotBeActive_WhenBeforeStartTime() {
        Banner banner = createTestBanner(
                now.plusDays(1),
                now.plusDays(2),
                true
        );

        assertFalse(service.shouldBeActive(banner, now));
    }

    @Test
    void shouldNotBeActive_WhenAfterEndTime() {
        Banner banner = createTestBanner(
                now.minusDays(2),
                now.minusDays(1),
                true
        );

        assertFalse(service.shouldBeActive(banner, now));
    }

    @Test
    void shouldNotBeActive_WhenBannerIsNull() {
        assertFalse(service.shouldBeActive(null, now));
    }

    @Test
    void filterActive_ReturnsOnlyActiveBanners() {
        Banner activeBanner = createTestBanner(now.minusDays(1), now.plusDays(1), true);
        Banner inactiveBanner = createTestBanner(now.minusDays(1), now.plusDays(1), true).deactivate();
        Banner futureBanner = createTestBanner(now.plusDays(1), now.plusDays(2), true);
        Banner expiredBanner = createTestBanner(now.minusDays(2), now.minusDays(1), true);

        List<Banner> banners = List.of(activeBanner, inactiveBanner, futureBanner, expiredBanner);
        List<Banner> active = service.filterActive(banners, now);

        assertEquals(1, active.size());
        assertTrue(active.contains(activeBanner));
    }

    @Test
    void filterActive_ReturnsEmptyList_WhenInputIsNull() {
        List<Banner> result = service.filterActive(null, now);
        assertTrue(result.isEmpty());
    }

    @Test
    void daysUntilActivation_ReturnsCorrectDays() {
        Banner banner = createTestBanner(now.plusDays(5), now.plusDays(10), true);

        Long days = service.daysUntilActivation(banner, now);

        assertEquals(5L, days);
    }

    @Test
    void daysUntilActivation_ReturnsNull_WhenAlreadyActive() {
        Banner banner = createTestBanner(now.minusDays(1), now.plusDays(1), true);

        Long days = service.daysUntilActivation(banner, now);

        assertNull(days);
    }

    @Test
    void daysUntilActivation_ReturnsNull_WhenBannerIsNull() {
        assertNull(service.daysUntilActivation(null, now));
    }

    @Test
    void daysUntilExpiration_ReturnsCorrectDays() {
        Banner banner = createTestBanner(now.minusDays(5), now.plusDays(3), true);

        Long days = service.daysUntilExpiration(banner, now);

        assertEquals(3L, days);
    }

    @Test
    void daysUntilExpiration_ReturnsNull_WhenAlreadyExpired() {
        Banner banner = createTestBanner(now.minusDays(5), now.minusDays(1), true);

        Long days = service.daysUntilExpiration(banner, now);

        assertNull(days);
    }

    @Test
    void daysUntilExpiration_ReturnsNull_WhenNoEndDate() {
        Banner banner = createTestBanner(now.minusDays(1), null, true);

        Long days = service.daysUntilExpiration(banner, now);

        assertNull(days);
    }

    @Test
    void isExpiringWithinDays_ReturnsTrue_WhenExpiringWithinPeriod() {
        Banner banner = createTestBanner(now.minusDays(5), now.plusDays(3), true);

        assertTrue(service.isExpiringWithinDays(banner, 5, now));
        assertTrue(service.isExpiringWithinDays(banner, 3, now));
    }

    @Test
    void isExpiringWithinDays_ReturnsFalse_WhenExpiringAfterPeriod() {
        Banner banner = createTestBanner(now.minusDays(5), now.plusDays(10), true);

        assertFalse(service.isExpiringWithinDays(banner, 5, now));
    }

    @Test
    void isExpiringWithinDays_ReturnsFalse_WhenNoEndDate() {
        Banner banner = createTestBanner(now.minusDays(1), null, true);

        assertFalse(service.isExpiringWithinDays(banner, 10, now));
    }

    @Test
    void isExpiringWithinDays_ThrowsException_WhenDaysIsNegative() {
        Banner banner = createTestBanner(now.minusDays(1), now.plusDays(1), true);

        assertThrows(IllegalArgumentException.class, () ->
                service.isExpiringWithinDays(banner, -1, now)
        );
    }

    @Test
    void filterExpiringWithinDays_ReturnsOnlyExpiringBanners() {
        Banner expiringSoon = createTestBanner(now.minusDays(5), now.plusDays(2), true);
        Banner expiringLater = createTestBanner(now.minusDays(5), now.plusDays(10), true);
        Banner noExpiry = createTestBanner(now.minusDays(1), null, true);

        List<Banner> banners = List.of(expiringSoon, expiringLater, noExpiry);
        List<Banner> expiring = service.filterExpiringWithinDays(banners, 3, now);

        assertEquals(1, expiring.size());
        assertTrue(expiring.contains(expiringSoon));
    }

    @Test
    void hasExpired_ReturnsTrue_WhenPastEndDate() {
        Banner banner = createTestBanner(now.minusDays(5), now.minusDays(1), true);

        assertTrue(service.hasExpired(banner, now));
    }

    @Test
    void hasExpired_ReturnsFalse_WhenBeforeEndDate() {
        Banner banner = createTestBanner(now.minusDays(5), now.plusDays(1), true);

        assertFalse(service.hasExpired(banner, now));
    }

    @Test
    void hasExpired_ReturnsFalse_WhenNoEndDate() {
        Banner banner = createTestBanner(now.minusDays(1), null, true);

        assertFalse(service.hasExpired(banner, now));
    }

    @Test
    void isFutureScheduled_ReturnsTrue_WhenStartTimeIsInFuture() {
        Banner banner = createTestBanner(now.plusDays(1), now.plusDays(5), true);

        assertTrue(service.isFutureScheduled(banner, now));
    }

    @Test
    void isFutureScheduled_ReturnsFalse_WhenStartTimeIsPast() {
        Banner banner = createTestBanner(now.minusDays(1), now.plusDays(1), true);

        assertFalse(service.isFutureScheduled(banner, now));
    }

    @Test
    void getSchedulingStatus_ReturnsScheduled_WhenFuture() {
        Banner banner = createTestBanner(now.plusDays(1), now.plusDays(5), true);

        assertEquals(BannerSchedulingStatus.SCHEDULED, service.getSchedulingStatus(banner, now));
    }

    @Test
    void getSchedulingStatus_ReturnsActive_WhenActiveAndWithinPeriod() {
        Banner banner = createTestBanner(now.minusDays(1), now.plusDays(1), true);

        assertEquals(BannerSchedulingStatus.ACTIVE, service.getSchedulingStatus(banner, now));
    }

    @Test
    void getSchedulingStatus_ReturnsInactive_WhenDeactivated() {
        Banner banner = createTestBanner(now.minusDays(1), now.plusDays(1), true);
        Banner deactivatedBanner = banner.deactivate();

        assertEquals(BannerSchedulingStatus.INACTIVE, service.getSchedulingStatus(deactivatedBanner, now));
    }

    @Test
    void getSchedulingStatus_ReturnsExpired_WhenPastEndDate() {
        Banner banner = createTestBanner(now.minusDays(5), now.minusDays(1), true);

        assertEquals(BannerSchedulingStatus.EXPIRED, service.getSchedulingStatus(banner, now));
    }

    @Test
    void getSchedulingStatus_ThrowsException_WhenBannerIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                service.getSchedulingStatus(null, now)
        );
    }

    @Test
    void shouldBeActive_UsesCurrentTime_WhenNowIsNull() {
        Banner banner = createTestBanner(
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1),
                true
        );

        assertTrue(service.shouldBeActive(banner, null));
    }
}
