package biz.ugur.busroutebackend.routing.application.response;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.application.dto.RouteSegmentDTO;
import biz.ugur.busroutebackend.routing.domain.enums.SegmentType;
import biz.ugur.busroutebackend.routing.domain.valueobjects.RouteSegment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TripOptionDTOConverterStopIdTest {

    private final TripOptionDTOConverter converter = new TripOptionDTOConverter(null, null);

    private RouteSegment busSegment() {
        return new RouteSegment(SegmentType.BUS_RIDE,
                Coordinates.of(37.90000, 58.30000),
                Coordinates.of(37.95000, 58.35000),
                10, "13", "ride");
    }

    @Test
    void busSegmentExposesStopIdOnFromAndToLocation() {
        RouteSegment segment = busSegment();
        segment.setFromStopId("stop-A");
        segment.setToStopId("stop-B");

        RouteSegmentDTO dto = converter.convertSegmentToDTO(segment);

        assertThat(dto.getFromLocation().getStopId()).isEqualTo("stop-A");
        assertThat(dto.getToLocation().getStopId()).isEqualTo("stop-B");
    }

    @Test
    void segmentWithoutStopIdLeavesStopIdNull() {
        RouteSegment walking = new RouteSegment(SegmentType.WALKING,
                Coordinates.of(37.90000, 58.30000),
                Coordinates.of(37.90500, 58.30500),
                3, null, "walk");

        RouteSegmentDTO dto = converter.convertSegmentToDTO(walking);

        assertThat(dto.getFromLocation().getStopId()).isNull();
        assertThat(dto.getToLocation().getStopId()).isNull();
    }
}
