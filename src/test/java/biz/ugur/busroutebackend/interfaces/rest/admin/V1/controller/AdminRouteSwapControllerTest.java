package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.routeswap.ReassignVehicleRequest;
import biz.ugur.busroutebackend.transport.application.dto.routeswap.OperatorReassignmentDTO;
import biz.ugur.busroutebackend.transport.application.dto.routeswap.ReassignVehicleCommand;
import biz.ugur.busroutebackend.transport.application.dto.routeswap.RouteSwapSummaryDTO;
import biz.ugur.busroutebackend.transport.application.dto.routeswap.VehicleReassignmentDTO;
import biz.ugur.busroutebackend.transport.application.usecase.routeswap.GetOperatorReassignmentsUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.routeswap.GetRouteSwapVerdictsUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.routeswap.VehicleRouteReassignmentUseCase;
import biz.ugur.busroutebackend.transport.infrastructure.monitoring.routeswap.RouteSwapProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminRouteSwapControllerTest {

    private final GetRouteSwapVerdictsUseCase verdictsUseCase = mock(GetRouteSwapVerdictsUseCase.class);
    private final VehicleRouteReassignmentUseCase reassignmentUseCase =
            mock(VehicleRouteReassignmentUseCase.class);
    private final GetOperatorReassignmentsUseCase reassignmentsUseCase =
            mock(GetOperatorReassignmentsUseCase.class);
    private final MessageSource messageSource = mock(MessageSource.class);
    private final RouteSwapProperties properties = new RouteSwapProperties();
    private final AdminRouteSwapController controller = new AdminRouteSwapController(
            verdictsUseCase, reassignmentUseCase, reassignmentsUseCase, properties, messageSource);

    private static VehicleReassignmentDTO reassignment() {
        return new VehicleReassignmentDTO("veh-1", "1903 AGH", "axis-old", "100",
                "axis-new", "66", LocalDate.of(2026, 8, 17), "FIRST",
                Instant.parse("2026-08-17T09:00:00Z"));
    }

    @Test
    void summaryCarriesReassignFlagState() {
        properties.setReassignEnabled(true);
        when(verdictsUseCase.summary()).thenReturn(Mono.just(new RouteSwapSummaryDTO(
                LocalDate.of(2026, 8, 17), "FIRST", 3, 40, 0, 5, 48, false)));

        StepVerifier.create(controller.getSummary())
                .assertNext(response -> {
                    assertThat(response.getBody().getData().reassignEnabled()).isTrue();
                    assertThat(response.getBody().getData().swapSuspected()).isEqualTo(3);
                })
                .verifyComplete();
    }

    @Test
    void summaryReportsDisabledReassignWhenFlagIsOff() {
        properties.setReassignEnabled(false);
        when(verdictsUseCase.summary()).thenReturn(Mono.just(new RouteSwapSummaryDTO(
                LocalDate.of(2026, 8, 17), "FIRST", 0, 0, 0, 0, 0, false)));

        StepVerifier.create(controller.getSummary())
                .assertNext(response ->
                        assertThat(response.getBody().getData().reassignEnabled()).isFalse())
                .verifyComplete();
    }

    @Test
    void reassignmentsListIsRejectedWhileFeatureFlagIsOff() {
        properties.setReassignEnabled(false);

        StepVerifier.create(controller.getReassignments())
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN))
                .verifyComplete();

        verify(reassignmentsUseCase, never()).activeReassignments();
    }

    @Test
    void enabledReassignmentsListDelegatesToUseCase() {
        properties.setReassignEnabled(true);
        when(reassignmentsUseCase.activeReassignments()).thenReturn(Mono.just(List.of(
                new OperatorReassignmentDTO("veh-1", "1903 AGH", "100", "66", "dispatcher-01",
                        Instant.parse("2026-08-17T05:30:00Z")))));

        StepVerifier.create(controller.getReassignments())
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().getData()).hasSize(1);
                })
                .verifyComplete();
    }

    @Test
    void reassignIsRejectedWhileFeatureFlagIsOff() {
        properties.setReassignEnabled(false);

        StepVerifier.create(controller.reassign(new ReassignVehicleRequest("veh-1", "66", "едет по 66-й")))
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN))
                .verifyComplete();

        verify(reassignmentUseCase, never()).reassign(any());
    }

    @Test
    void revertIsRejectedWhileFeatureFlagIsOff() {
        properties.setReassignEnabled(false);

        StepVerifier.create(controller.revert("veh-1"))
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN))
                .verifyComplete();

        verify(reassignmentUseCase, never()).revert(anyString());
    }

    @Test
    void enabledReassignDelegatesRequestToUseCase() {
        properties.setReassignEnabled(true);
        when(reassignmentUseCase.reassign(new ReassignVehicleCommand("veh-1", "66", "едет по 66-й")))
                .thenReturn(Mono.just(reassignment()));

        StepVerifier.create(controller.reassign(new ReassignVehicleRequest("veh-1", "66", "едет по 66-й")))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().getData().newRouteNumber()).isEqualTo("66");
                })
                .verifyComplete();
    }

    @Test
    void enabledRevertDelegatesVehicleIdToUseCase() {
        properties.setReassignEnabled(true);
        when(reassignmentUseCase.revert("veh-1")).thenReturn(Mono.just(reassignment()));

        StepVerifier.create(controller.revert("veh-1"))
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();

        verify(reassignmentUseCase).revert("veh-1");
    }
}
