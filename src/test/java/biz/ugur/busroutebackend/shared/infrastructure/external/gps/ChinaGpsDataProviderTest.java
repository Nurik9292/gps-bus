package biz.ugur.busroutebackend.shared.infrastructure.external.gps;

import biz.ugur.busroutebackend.shared.infrastructure.external.gps.config.GpsProviderProperties;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.reactive.function.client.WebClient;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChinaGpsDataProviderTest {

    private ChinaGpsDataProvider provider;
    private Method countFresh;
    private Method latestFixTime;

    @BeforeEach
    void setUp() throws Exception {
        WebClient webClient = Mockito.mock(WebClient.class);
        GpsProviderProperties properties = new GpsProviderProperties();
        provider = new ChinaGpsDataProvider(
                webClient, properties, "test-token", true, Optional.empty());

        countFresh = ChinaGpsDataProvider.class.getDeclaredMethod("countFresh", List.class);
        countFresh.setAccessible(true);
        latestFixTime = ChinaGpsDataProvider.class.getDeclaredMethod("latestFixTime", List.class);
        latestFixTime.setAccessible(true);
    }

    @Test
    void countFreshReturnsZeroWhenAllPositionsAreStale() throws Exception {
        LocalDateTime elevenHoursAgo = LocalDateTime.now(ZoneOffset.UTC).minusHours(11);
        List<GpsPositionDTO> positions = List.of(
                positionWithFixTime(elevenHoursAgo),
                positionWithFixTime(elevenHoursAgo.minusMinutes(30)),
                positionWithFixTime(elevenHoursAgo.minusHours(1))
        );

        int fresh = (int) countFresh.invoke(provider, positions);

        assertEquals(0, fresh);
    }

    @Test
    void countFreshIncludesPositionsNewerThanFiveMinutes() throws Exception {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<GpsPositionDTO> positions = List.of(
                positionWithFixTime(now.minusSeconds(30)),
                positionWithFixTime(now.minusMinutes(4)),
                positionWithFixTime(now.minusMinutes(10))
        );

        int fresh = (int) countFresh.invoke(provider, positions);

        assertEquals(2, fresh);
    }

    @Test
    void countFreshIgnoresPositionsWithNullFixTime() throws Exception {
        List<GpsPositionDTO> positions = List.of(
                positionWithFixTime(null),
                positionWithFixTime(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(10))
        );

        int fresh = (int) countFresh.invoke(provider, positions);

        assertEquals(1, fresh);
    }

    @Test
    void latestFixTimeReturnsNullWhenAllFixTimesNull() throws Exception {
        List<GpsPositionDTO> positions = List.of(
                positionWithFixTime(null),
                positionWithFixTime(null)
        );

        Object latest = latestFixTime.invoke(provider, positions);

        assertNull(latest);
    }

    @Test
    void latestFixTimeReturnsMaxAcrossPositions() throws Exception {
        LocalDateTime older = LocalDateTime.now(ZoneOffset.UTC).minusHours(11);
        LocalDateTime newer = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(2);
        List<GpsPositionDTO> positions = List.of(
                positionWithFixTime(older),
                positionWithFixTime(newer),
                positionWithFixTime(null)
        );

        Instant latest = (Instant) latestFixTime.invoke(provider, positions);

        assertNotNull(latest);
        Instant expected = newer.toInstant(ZoneOffset.UTC);
        assertTrue(Math.abs(latest.toEpochMilli() - expected.toEpochMilli()) < 1000);
    }

    private GpsPositionDTO positionWithFixTime(LocalDateTime fixTime) {
        GpsPositionDTO p = new GpsPositionDTO();
        p.setFixTime(fixTime);
        return p;
    }
}
