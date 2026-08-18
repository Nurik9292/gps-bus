package biz.ugur.busroutebackend.transport.domain.exceptions;

import biz.ugur.busroutebackend.shared.infrastructure.exception.HttpStatusMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusStopInUseExceptionTest {

    @Test
    void mapsToConflictStatus() {
        BusStopInUseException exception = new BusStopInUseException("stop-1", List.of("7", "110"));

        assertThat(HttpStatusMapper.mapFromException(exception)).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void messageNamesBlockingRoutes() {
        BusStopInUseException exception = new BusStopInUseException("stop-1", List.of("7", "110"));

        assertThat(exception.getMessage()).contains("7, 110");
        assertThat(exception.getRouteNumbers()).containsExactly("7", "110");
    }
}
