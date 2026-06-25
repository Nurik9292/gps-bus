package biz.ugur.busroutebackend.routing.application.builders;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.application.factory.RouteSegmentFactory;
import biz.ugur.busroutebackend.routing.domain.enums.SegmentType;
import biz.ugur.busroutebackend.routing.domain.valueobjects.RouteSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransferSegmentBuilderTest {

    private static final Coordinates ALIGHT = Coordinates.of(37.92316, 58.39031);
    private static final Coordinates BOARD = Coordinates.of(37.92261, 58.38822);

    private final WalkingTimeCalculator walkingTimeCalculator = mock(WalkingTimeCalculator.class);
    private final RouteSegmentFactory routeSegmentFactory = new RouteSegmentFactory();
    private final TransferSegmentBuilder builder =
            new TransferSegmentBuilder(walkingTimeCalculator, routeSegmentFactory);

    @Test
    void differentStops_producesWalkWithGeometryThenTransferAtBoard() {
        when(walkingTimeCalculator.calculateWalkingTime(ALIGHT, BOARD)).thenReturn(3);

        List<RouteSegment> segments = builder.build(ALIGHT, "alight", BOARD, "board", 5);

        assertThat(segments).hasSize(2);
        RouteSegment walk = segments.get(0);
        RouteSegment transfer = segments.get(1);

        assertThat(walk.getType()).isEqualTo(SegmentType.WALKING);
        assertThat(walk.getWalkingGeometry()).isNotNull();
        assertThat(walk.getFromLocation()).isEqualTo(ALIGHT);
        assertThat(walk.getToLocation()).isEqualTo(BOARD);
        assertThat(walk.getDurationMinutes()).isEqualTo(3);

        assertThat(transfer.getType()).isEqualTo(SegmentType.TRANSFER);
        assertThat(transfer.getFromLocation()).isEqualTo(BOARD);
        assertThat(transfer.getDurationMinutes()).isEqualTo(2);

        assertThat(walk.getDurationMinutes() + transfer.getDurationMinutes()).isEqualTo(5);
    }

    @Test
    void sameStop_producesSingleTransfer() {
        List<RouteSegment> segments = builder.build(ALIGHT, "stop", ALIGHT, "stop", 4);

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).getType()).isEqualTo(SegmentType.TRANSFER);
        assertThat(segments.get(0).getDurationMinutes()).isEqualTo(4);
    }
}
