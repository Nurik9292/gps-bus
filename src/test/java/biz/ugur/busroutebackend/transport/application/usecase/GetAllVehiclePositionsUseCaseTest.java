package biz.ugur.busroutebackend.transport.application.usecase;

import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO.GpsAttributesDTO;
import biz.ugur.busroutebackend.transport.application.service.GpsDataAggregatorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAllVehiclePositionsUseCaseTest {

    @Mock
    private GpsDataAggregatorService gpsDataAggregator;

    @InjectMocks
    private GetAllVehiclePositionsUseCase useCase;

    @Test
    void returnsEmptyListWhenAggregatorErrors() {
        when(gpsDataAggregator.fetchAllPositionsFromAllProviders())
                .thenReturn(Mono.error(new RuntimeException("CHINA + TUGDK both down")));

        StepVerifier.create(useCase.execute(null, null))
                .assertNext(positions ->
                        org.assertj.core.api.Assertions.assertThat(positions).isEmpty())
                .verifyComplete();
    }

    @Test
    void returnsAllPositionsWhenActiveIsNull() {
        when(gpsDataAggregator.fetchAllPositionsFromAllProviders())
                .thenReturn(Mono.just(List.of(positionMoving("d1"), positionStopped("d2"))));

        StepVerifier.create(useCase.execute(null, null))
                .assertNext(positions ->
                        org.assertj.core.api.Assertions.assertThat(positions).hasSize(2))
                .verifyComplete();
    }

    @Test
    void filtersOnlyMotionWhenActiveIsTrue() {
        when(gpsDataAggregator.fetchAllPositionsFromAllProviders())
                .thenReturn(Mono.just(List.of(positionMoving("d1"), positionStopped("d2"))));

        StepVerifier.create(useCase.execute(null, true))
                .assertNext(positions -> {
                    org.assertj.core.api.Assertions.assertThat(positions).hasSize(1);
                    org.assertj.core.api.Assertions.assertThat(positions.getFirst().getDeviceId())
                            .isEqualTo("d1");
                })
                .verifyComplete();
    }

    @Test
    void appliesLimitAfterFiltering() {
        when(gpsDataAggregator.fetchAllPositionsFromAllProviders())
                .thenReturn(Mono.just(List.of(
                        positionMoving("d1"),
                        positionMoving("d2"),
                        positionMoving("d3"))));

        StepVerifier.create(useCase.execute(2, null))
                .assertNext(positions ->
                        org.assertj.core.api.Assertions.assertThat(positions).hasSize(2))
                .verifyComplete();
    }

    private GpsPositionDTO positionMoving(String deviceId) {
        GpsPositionDTO p = new GpsPositionDTO();
        p.setDeviceId(deviceId);
        GpsAttributesDTO attrs = new GpsAttributesDTO();
        attrs.setMotion(true);
        p.setAttributes(attrs);
        return p;
    }

    private GpsPositionDTO positionStopped(String deviceId) {
        GpsPositionDTO p = new GpsPositionDTO();
        p.setDeviceId(deviceId);
        GpsAttributesDTO attrs = new GpsAttributesDTO();
        attrs.setMotion(false);
        p.setAttributes(attrs);
        return p;
    }
}
