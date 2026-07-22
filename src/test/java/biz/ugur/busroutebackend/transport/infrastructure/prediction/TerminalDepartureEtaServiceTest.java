package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo;
import biz.ugur.busroutebackend.transport.domain.valueobject.SegmentTravelStat;
import biz.ugur.busroutebackend.transport.infrastructure.config.TerminalDepartureProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TerminalDepartureEtaServiceTest {

    private static final Instant ARRIVED = Instant.parse("2026-07-22T05:00:00Z");
    private static final String ROUTE_ID = "route-legacy-31";

    @Mock
    private RouteGeometryCache routeGeometryCache;
    @Mock
    private VehiclePositionPredictor predictor;

    private TerminalDepartureProperties properties;
    private TerminalDwellSnapshotHolder dwellHolder;
    private TerminalPresenceHolder presenceHolder;
    private LiveFactorSnapshotHolder liveHolder;
    private TerminalDepartureEtaService service;

    private static RouteStopInfo stop(String id, String name, int distMeters) {
        return new RouteStopInfo(id, name, null, null, 1, null, distMeters,
                BigDecimal.valueOf(37.95), BigDecimal.valueOf(58.38), false);
    }

    private static TerminalPresenceHolder.TerminalPresence presence() {
        return new TerminalPresenceHolder.TerminalPresence("23", 0, ARRIVED);
    }

    @BeforeEach
    void setUp() {
        properties = new TerminalDepartureProperties();
        properties.setMode(TerminalDepartureProperties.Mode.LIVE);
        dwellHolder = new TerminalDwellSnapshotHolder();
        presenceHolder = new TerminalPresenceHolder();
        liveHolder = new LiveFactorSnapshotHolder();
        service = new TerminalDepartureEtaService(properties, dwellHolder, presenceHolder,
                routeGeometryCache, predictor, liveHolder);

        dwellHolder.publish(Map.of(
                TerminalDwellSnapshotHolder.key("23", 0, 10),
                new TerminalDwellSnapshotHolder.DwellStat(300.0, 10)));
        when(routeGeometryCache.getRouteStops(eq(ROUTE_ID), eq(1))).thenReturn(List.of(
                stop("T", "Конечная", 0),
                stop("S1", "Первая", 800),
                stop("S2", "Вторая", 1700),
                stop("S3", "Третья", 2600)));
        when(predictor.getSegmentTravelStat(anyString(), anyInt(), anyString(), anyString(),
                anyInt(), anyBoolean())).thenReturn(null);
    }

    @Test
    void remainingDwellPlusSegmentsWithHistoricalStats() {
        when(predictor.getSegmentTravelStat(eq("23"), eq(1), eq("T"), eq("S1"), anyInt(), anyBoolean()))
                .thenReturn(SegmentTravelStat.initial("23", 1, "T", "S1", 10, false)
                        .withNewSample(90.0, ARRIVED).withNewSample(90.0, ARRIVED)
                        .withNewSample(90.0, ARRIVED));

        var etas = service.departureEtas(presence(), ROUTE_ID, ARRIVED.plusSeconds(120));

        assertThat(etas).hasSize(3);
        assertThat(etas.get(0).stopId()).isEqualTo("T");
        assertThat(etas.get(0).cumulativeSeconds()).isEqualTo(180);
        assertThat(etas.get(0).distanceMeters()).isZero();
        assertThat(etas.get(1).stopId()).isEqualTo("S1");
        assertThat(etas.get(1).cumulativeSeconds()).isEqualTo(270);
        assertThat(etas.get(1).distanceMeters()).isEqualTo(800);
        assertThat(etas.get(2).cumulativeSeconds()).isGreaterThan(270);
    }

    @Test
    void thinDwellSamplesProduceNothing() {
        dwellHolder.publish(Map.of(
                TerminalDwellSnapshotHolder.key("23", 0, 10),
                new TerminalDwellSnapshotHolder.DwellStat(300.0, 4)));
        assertThat(service.departureEtas(presence(), ROUTE_ID, ARRIVED.plusSeconds(60))).isEmpty();
    }

    @Test
    void overstayedDwellUsesFloor() {
        var etas = service.departureEtas(presence(), ROUTE_ID, ARRIVED.plusSeconds(500));
        assertThat(etas).isNotEmpty();
        assertThat(etas.get(0).cumulativeSeconds()).isEqualTo(60);
    }

    @Test
    void hungBusBeyondDwellMaxProducesNothing() {
        assertThat(service.departureEtas(presence(), ROUTE_ID, ARRIVED.plusSeconds(3700))).isEmpty();
    }

    @Test
    void offModeProducesNothing() {
        properties.setMode(TerminalDepartureProperties.Mode.OFF);
        assertThat(service.departureEtas(presence(), ROUTE_ID, ARRIVED.plusSeconds(60))).isEmpty();
    }

    @Test
    void liveFactorStretchesFirstSegment() {
        liveHolder.publish(Map.of("T|S1", 2.0));
        when(predictor.getSegmentTravelStat(eq("23"), eq(1), eq("T"), eq("S1"), anyInt(), anyBoolean()))
                .thenReturn(SegmentTravelStat.initial("23", 1, "T", "S1", 10, false)
                        .withNewSample(100.0, ARRIVED).withNewSample(100.0, ARRIVED)
                        .withNewSample(100.0, ARRIVED));

        var etas = service.departureEtas(presence(), ROUTE_ID, ARRIVED.plusSeconds(120));

        assertThat(etas.get(1).cumulativeSeconds() - etas.get(0).cumulativeSeconds())
                .isEqualTo(200);
    }

    @Test
    void vehicleLookupPathUsesPresenceHolder() {
        presenceHolder.arrived("veh-9", "23", 0, ARRIVED);
        var etas = service.departureEtasForVehicle("veh-9", "23", ROUTE_ID, ARRIVED.plusSeconds(120));
        assertThat(etas).isNotEmpty();
        assertThat(etas.get(0).departDirection()).isEqualTo(1);
        assertThat(service.departureEtasForVehicle("veh-unknown", "23", ROUTE_ID,
                ARRIVED.plusSeconds(120))).isEmpty();
    }

    @Test
    void reassignedVehicleWithStalePresenceIsSilent() {
        presenceHolder.arrived("veh-9", "57", 0, ARRIVED);
        assertThat(service.departureEtasForVehicle("veh-9", "23", ROUTE_ID,
                ARRIVED.plusSeconds(120))).isEmpty();
    }

    @Test
    void dwellIsLookedUpByArrivalHourNotCurrentHour() {
        var arrivedLateInHour = Instant.parse("2026-07-22T05:55:00Z");
        var nowNextHour = Instant.parse("2026-07-22T06:10:00Z");
        assertThat(java.time.LocalDateTime.ofInstant(nowNextHour,
                java.time.ZoneId.of("Asia/Ashgabat")).getHour()).isEqualTo(11);
        var lateArrival = new TerminalPresenceHolder.TerminalPresence("23", 0, arrivedLateInHour);
        var etas = service.departureEtas(lateArrival, ROUTE_ID, nowNextHour);
        assertThat(etas).as("отстой ищется по часу прибытия (10), не по текущему (11)")
                .isNotEmpty();
    }
}
