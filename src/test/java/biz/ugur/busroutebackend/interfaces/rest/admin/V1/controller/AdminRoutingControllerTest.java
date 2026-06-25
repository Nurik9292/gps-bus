package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.routing.infrastructure.raptor.RaptorTimetableCache;
import biz.ugur.busroutebackend.routing.infrastructure.raptor.RaptorTimetableGenerator;
import biz.ugur.busroutebackend.routing.infrastructure.raptor.RaptorTransferGenerator;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminRoutingControllerTest {

    private final RaptorTimetableGenerator timetableGenerator = mock(RaptorTimetableGenerator.class);
    private final RaptorTransferGenerator transferGenerator = mock(RaptorTransferGenerator.class);
    private final RaptorTimetableCache timetableCache = mock(RaptorTimetableCache.class);
    private final AdminRoutingController controller =
            new AdminRoutingController(timetableGenerator, transferGenerator, timetableCache);

    @Test
    void invalidatesCacheAfterRegeneratingTimetable() {
        when(timetableGenerator.regenerate())
                .thenReturn(Mono.just(new RaptorTimetableGenerator.GenerationResult(10, 100, 0)));
        when(transferGenerator.regenerate())
                .thenReturn(Mono.just(new RaptorTransferGenerator.GenerationResult(50, 25)));

        StepVerifier.create(controller.regenerateTimetable())
                .assertNext(report -> {
                    assertThat(report.trips()).isEqualTo(10);
                    assertThat(report.stopTimes()).isEqualTo(100);
                    assertThat(report.stopTransfers()).isEqualTo(50);
                })
                .verifyComplete();

        verify(timetableCache).invalidate();
    }
}
