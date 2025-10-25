package biz.ugur.busroutebackend.banner.domain.valueobjects;

import biz.ugur.busroutebackend.banner.domain.model.Banner;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class BannerPeriodTest {

    @Test
    void between() {
        LocalDateTime startTime = LocalDateTime.now();
        LocalDateTime endTime = LocalDateTime.now().plusDays(1);
        BannerPeriod period = BannerPeriod.between(startTime, endTime);

        assertNotNull(period);
        assertTrue(period.getStartTime().isEqual(startTime));
        assertTrue(period.getEndTime().isEqual(endTime));
        assertTrue(period.getStartTime().isBefore(endTime));
    }

    @Test
    void startingNowUntil() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endTime = now.plusDays(1);
        BannerPeriod period = BannerPeriod.startingNowUntil(endTime);

        assertNotNull(period);
        assertNotNull(period.getStartTime());
        assertTrue(period.getEndTime().isEqual(endTime));
        assertTrue(period.getStartTime().isBefore(endTime));
    }

    @Test
    void startingAt() {
        LocalDateTime startTime = LocalDateTime.now();
        BannerPeriod period = BannerPeriod.startingAt(startTime);

        assertNotNull(period);
        assertTrue(period.getStartTime().isEqual(startTime));
        assertNull(period.getEndTime());
    }

    @Test
    void startingNow() {
        BannerPeriod period = BannerPeriod.startingNow();

        assertNotNull(period);
        assertNotNull(period.getStartTime());
        assertNull(period.getEndTime());
    }

    @Test
    void isActiveWithEndTime() {
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);
        BannerPeriod period = BannerPeriod.between(start, end);

        // Сейчас период активен
        assertTrue(period.isActive(LocalDateTime.now()));

        // Перед стартом
        assertFalse(period.isActive(start.minusSeconds(1)));

        // После конца
        assertFalse(period.isActive(end.plusSeconds(1)));
    }

    @Test
    void isActiveWithoutEndTime() {
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        BannerPeriod period = BannerPeriod.startingAt(start);

        // Сейчас период активен
        assertTrue(period.isActive(LocalDateTime.now()));

        // Перед стартом
        assertFalse(period.isActive(start.minusSeconds(1)));

        // Без передачи now (null), метод должен работать
        assertTrue(period.isActive(null));
    }

    @Test
    void invalidEndTimeThrowsException() {
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.minusDays(1);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> BannerPeriod.between(start, end)
        );
        assertEquals("Banner Period endTime cannot be before startTime", exception.getMessage());
    }

    @Test
    void nullStartTimeUsesNow() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        BannerPeriod period = BannerPeriod.between(null, LocalDateTime.now().plusDays(1));

        assertNotNull(period.getStartTime());
        assertTrue(period.getStartTime().isAfter(before));
    }
}