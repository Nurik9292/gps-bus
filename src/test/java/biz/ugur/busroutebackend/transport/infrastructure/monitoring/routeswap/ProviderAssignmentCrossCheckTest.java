package biz.ugur.busroutebackend.transport.infrastructure.monitoring.routeswap;

import biz.ugur.busroutebackend.shared.infrastructure.email.AlertQuietHours;
import biz.ugur.busroutebackend.shared.infrastructure.email.EmailNotificationService;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.monitoring.AlertKind;
import biz.ugur.busroutebackend.transport.application.dto.BusInfoDTO;
import biz.ugur.busroutebackend.transport.application.services.ExternalApiService;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.RouteSwapAuditRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProviderAssignmentCrossCheckTest {

    @Mock
    private ExternalApiService externalApiService;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private RouteSwapAuditRepository auditRepository;
    @Mock
    private EmailNotificationService emailService;
    @Mock
    private AlertQuietHours quietHours;

    private RouteSwapProperties properties;
    private ProviderAssignmentCrossCheck crossCheck;

    @BeforeEach
    void setUp() {
        properties = new RouteSwapProperties();
        properties.setEnabled(true);
        properties.setProviderCheckEnabled(true);
        properties.setRecipients("ops@busroute.tm");
        when(quietHours.active()).thenReturn(false);
        when(emailService.sendGpsAlert(anyList(), anyString(), any(), anyString(), anyString()))
                .thenReturn(Mono.empty());
        when(auditRepository.tryRecordVerdict(anyString(), any(), any(), anyString(), anyString(),
                any(), any(), anyString())).thenReturn(Mono.just(true));
        crossCheck = new ProviderAssignmentCrossCheck(properties, externalApiService, vehicleRepository,
                auditRepository, emailService, quietHours,
                java.time.Clock.fixed(java.time.Instant.parse("2026-08-08T06:00:00Z"),
                        java.time.ZoneOffset.UTC));
    }

    private static BusInfoDTO registryEntry(String plate, String route) {
        BusInfoDTO dto = new BusInfoDTO();
        dto.setCarNumber(plate);
        dto.setRouteNumber(route);
        return dto;
    }

    private static Vehicle vehicle(String id, String plate, String routeNumber) {
        return Vehicle.builder()
                .id(new VehicleId(id))
                .licensePlate(plate)
                .routeNumber(routeNumber)
                .isActive(true)
                .lastPositionUpdate(java.time.LocalDateTime.now())
                .build();
    }

    private static Vehicle offlineVehicle(String id, String plate) {
        return Vehicle.builder()
                .id(new VehicleId(id))
                .licensePlate(plate)
                .routeNumber(null)
                .isActive(true)
                .lastPositionUpdate(java.time.LocalDateTime.now().minusHours(5))
                .build();
    }

    @Test
    void mismatchIsPersistedAndMailed() {
        when(externalApiService.fetchAllBusInfo()).thenReturn(Mono.just(List.of(
                registryEntry("1128 AGJ", "103"),
                registryEntry("1903 AGH", "100"))));
        when(vehicleRepository.findActiveVehicles()).thenReturn(Flux.just(
                vehicle("veh-1", "1128 AGJ", "29"),
                vehicle("veh-2", "1903 AGH", "100")));

        StepVerifier.create(crossCheck.checkNow()).verifyComplete();

        verify(auditRepository, times(1)).tryRecordVerdict(eq("1128 AGJ"), eq("veh-1"), eq("29"),
                eq("PROVIDER_MISMATCH"), contains("провайдер=r103"), eq("103"), any(), anyString());
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendGpsAlert(
                eq(properties.recipientList()), anyString(), eq(AlertKind.ROUTE_SWAP),
                anyString(), body.capture());
        assertTrue(body.getValue().contains("1128 AGJ"), body.getValue());
    }

    @Test
    void missingDbAssignmentCountsAsMismatch() {
        when(externalApiService.fetchAllBusInfo()).thenReturn(Mono.just(List.of(
                registryEntry("1146 AGG", "25"))));
        when(vehicleRepository.findActiveVehicles()).thenReturn(Flux.just(
                vehicle("veh-3", "1146 AGG", null)));

        StepVerifier.create(crossCheck.checkNow()).verifyComplete();

        verify(auditRepository).tryRecordVerdict(eq("1146 AGG"), eq("veh-3"), isNull(),
                eq("PROVIDER_MISMATCH"), contains("нет назначения"), eq("25"), any(), anyString());
    }

    @Test
    void emptyRegistrySkipsSilently() {
        when(externalApiService.fetchAllBusInfo()).thenReturn(Mono.just(List.of()));
        when(vehicleRepository.findActiveVehicles()).thenReturn(Flux.empty());

        StepVerifier.create(crossCheck.checkNow()).verifyComplete();

        verify(emailService, never()).sendGpsAlert(anyList(), anyString(), any(), anyString(), anyString());
        verify(auditRepository, never()).tryRecordVerdict(anyString(), any(), any(), anyString(),
                anyString(), any(), any(), anyString());
    }

    @Test
    void duplicateRunSendsNoRepeatMail() {
        when(externalApiService.fetchAllBusInfo()).thenReturn(Mono.just(List.of(
                registryEntry("1128 AGJ", "103"))));
        when(vehicleRepository.findActiveVehicles()).thenReturn(Flux.just(
                vehicle("veh-1", "1128 AGJ", "29")));
        when(auditRepository.tryRecordVerdict(anyString(), any(), any(), anyString(), anyString(),
                any(), any(), anyString())).thenReturn(Mono.just(false));

        StepVerifier.create(crossCheck.checkNow()).verifyComplete();

        verify(emailService, never()).sendGpsAlert(anyList(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void persistFailureOfOneMismatchDoesNotBlockOthers() {
        when(externalApiService.fetchAllBusInfo()).thenReturn(Mono.just(List.of(
                registryEntry("1128 AGJ", "103"),
                registryEntry("1146 AGG", "25"))));
        when(vehicleRepository.findActiveVehicles()).thenReturn(Flux.just(
                vehicle("veh-1", "1128 AGJ", "29"),
                vehicle("veh-3", "1146 AGG", "49")));
        when(auditRepository.tryRecordVerdict(eq("1128 AGJ"), any(), any(), anyString(), anyString(),
                any(), any(), anyString())).thenReturn(Mono.error(new IllegalStateException("db down")));
        when(auditRepository.tryRecordVerdict(eq("1146 AGG"), any(), any(), anyString(), anyString(),
                any(), any(), anyString())).thenReturn(Mono.just(true));

        StepVerifier.create(crossCheck.checkNow()).verifyComplete();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendGpsAlert(anyList(), anyString(), any(), anyString(), body.capture());
        assertTrue(body.getValue().contains("1146 AGG"), body.getValue());
    }

    @Test
    void quietHoursBufferLinesUntilNextRun() {
        when(externalApiService.fetchAllBusInfo()).thenReturn(Mono.just(List.of(
                registryEntry("1128 AGJ", "103"))));
        when(vehicleRepository.findActiveVehicles()).thenReturn(Flux.just(
                vehicle("veh-1", "1128 AGJ", "29")));
        when(quietHours.active()).thenReturn(true);

        StepVerifier.create(crossCheck.checkNow()).verifyComplete();
        verify(emailService, never()).sendGpsAlert(anyList(), anyString(), any(), anyString(), anyString());

        when(quietHours.active()).thenReturn(false);
        when(auditRepository.tryRecordVerdict(anyString(), any(), any(), anyString(), anyString(),
                any(), any(), anyString())).thenReturn(Mono.just(false));

        StepVerifier.create(crossCheck.checkNow()).verifyComplete();
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendGpsAlert(anyList(), anyString(), any(), anyString(), body.capture());
        assertTrue(body.getValue().contains("1128 AGJ"), body.getValue());
    }

    @Test
    void offlineUnassignedVehicleIsNotReported() {
        when(externalApiService.fetchAllBusInfo()).thenReturn(Mono.just(List.of(
                registryEntry("1662 AGG", "71"))));
        when(vehicleRepository.findActiveVehicles()).thenReturn(Flux.just(
                offlineVehicle("veh-9", "1662 AGG")));

        StepVerifier.create(crossCheck.checkNow()).verifyComplete();

        verify(auditRepository, never()).tryRecordVerdict(anyString(), any(), any(), anyString(),
                anyString(), any(), any(), anyString());
    }

    @Test
    void detectorPureLogicHandlesMatchesAndUnknownPlates() {
        List<ProviderMismatchDetector.Mismatch> mismatches = ProviderMismatchDetector.detect(
                List.of(registryEntry("1128 AGJ", "29"), registryEntry("9999 XXX", "1")),
                List.of(vehicle("veh-1", "1128 AGJ", "29"), vehicle("veh-4", "1476 AGJ", "14")));

        assertEquals(0, mismatches.size());
    }
}
