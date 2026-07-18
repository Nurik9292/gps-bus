package biz.ugur.busroutebackend.transport.application.usecase.assignment;

import biz.ugur.busroutebackend.transport.domain.valueobject.CityId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.transport.application.dto.assignment.ExcelImportRow;
import biz.ugur.busroutebackend.transport.application.dto.assignment.ImportFromExcelCommand;
import biz.ugur.busroutebackend.transport.application.dto.assignment.RouteAssignmentData;
import biz.ugur.busroutebackend.transport.application.mapper.RouteAssignmentDataMapper;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.GpsProviderType;
import biz.ugur.busroutebackend.transport.infrastructure.excel.ExcelRouteAssignmentParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class ImportRouteAssignmentsFromExcelUseCaseTest {

    @InjectMocks
    private ImportRouteAssignmentsFromExcelUseCase useCase;

    @Mock
    private CorrelationContextService correlationService;
    @Mock
    private EventBus eventBus;
    @Mock
    private ExcelRouteAssignmentParser excelParser;
    @Mock
    private RouteAssignmentRepository assignmentRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private BusRouteRepository busRouteRepository;
    @Mock
    private RouteAssignmentDataMapper dataMapper;

    private static final byte[] FILE = new byte[]{1};
    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    private void stubCorrelation() {
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private ExcelImportRow row(String routeNumber) {
        return new ExcelImportRow(2, "1111 AGH", routeNumber, TOMORROW, null, "FIRST");
    }

    private Vehicle vehicleInCity(String cityId) {
        CityId city = cityId == null ? null : CityId.of(cityId);
        return Vehicle.create("device-1", "1111 AGH", GpsProviderType.defaultProvider(), city);
    }

    private BusRoute route(String id, String number) {
        return BusRoute.builder()
                .id(BusRouteId.of(id))
                .routeNumber(number)
                .isActive(true)
                .build();
    }

    private void stubHappyTail() {
        when(assignmentRepository.existsActiveByVehicleAndDateAndShift(any(), any(), any()))
                .thenReturn(Mono.just(false));
        when(assignmentRepository.save(any(RouteAssignment.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(dataMapper.toRouteAssignmentData(any(RouteAssignment.class)))
                .thenReturn(Mono.just(new RouteAssignmentData(
                        "a-1", "v-1", "1111 AGH", "device-1", "r-1", "1", "Route",
                        TOMORROW, "FIRST", null, null, "op", null, null,
                        true, false, false, null, null, 1L)));
    }

    @Test
    void vehicleWithCityGetsRouteOfItsOwnCity() {
        stubCorrelation();
        stubHappyTail();
        when(excelParser.parse(FILE)).thenReturn(Mono.just(List.of(row("1"))));
        when(vehicleRepository.findByLicensePlate("1111 AGH"))
                .thenReturn(Mono.just(vehicleInCity("city-turkmenbashi")));
        when(busRouteRepository.findByRouteNumberAndCityId("1", "city-turkmenbashi"))
                .thenReturn(Mono.just(route("route-tb-1", "1")));

        StepVerifier.create(useCase.execute(Mono.just(new ImportFromExcelCommand(FILE, "op"))))
                .assertNext(result -> {
                    assertThat(result.successCount()).isEqualTo(1);
                    assertThat(result.failedCount()).isZero();
                })
                .verifyComplete();

        verify(busRouteRepository).findByRouteNumberAndCityId("1", "city-turkmenbashi");
        verify(busRouteRepository, never()).findPreferredByRouteNumber(anyString());
    }

    @Test
    void missingRouteInVehicleCityFailsRowInsteadOfForeignCityAssignment() {
        stubCorrelation();
        when(excelParser.parse(FILE)).thenReturn(Mono.just(List.of(row("77"))));
        when(vehicleRepository.findByLicensePlate("1111 AGH"))
                .thenReturn(Mono.just(vehicleInCity("city-arkadag")));
        when(busRouteRepository.findByRouteNumberAndCityId("77", "city-arkadag"))
                .thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(new ImportFromExcelCommand(FILE, "op"))))
                .assertNext(result -> {
                    assertThat(result.successCount()).isZero();
                    assertThat(result.failedCount()).isEqualTo(1);
                    assertThat(result.failed().get(0).error()).contains("not found in city");
                })
                .verifyComplete();

        verify(busRouteRepository, never()).findPreferredByRouteNumber(anyString());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void vehicleWithoutCityAndUniqueRouteNumberIsAssigned() {
        stubCorrelation();
        stubHappyTail();
        when(excelParser.parse(FILE)).thenReturn(Mono.just(List.of(row("25"))));
        when(vehicleRepository.findByLicensePlate("1111 AGH"))
                .thenReturn(Mono.just(vehicleInCity(null)));
        when(busRouteRepository.findActiveByRouteNumber("25"))
                .thenReturn(reactor.core.publisher.Flux.just(route("route-ash-25", "25")));

        StepVerifier.create(useCase.execute(Mono.just(new ImportFromExcelCommand(FILE, "op"))))
                .assertNext(result -> assertThat(result.successCount()).isEqualTo(1))
                .verifyComplete();

        verify(busRouteRepository).findActiveByRouteNumber("25");
    }

    @Test
    void vehicleWithoutCityAndAmbiguousRouteNumberFailsRow() {
        stubCorrelation();
        when(excelParser.parse(FILE)).thenReturn(Mono.just(List.of(row("1"))));
        when(vehicleRepository.findByLicensePlate("1111 AGH"))
                .thenReturn(Mono.just(vehicleInCity(null)));
        when(busRouteRepository.findActiveByRouteNumber("1"))
                .thenReturn(reactor.core.publisher.Flux.just(
                        route("route-ash-1", "1"), route("route-tb-1", "1")));

        StepVerifier.create(useCase.execute(Mono.just(new ImportFromExcelCommand(FILE, "op"))))
                .assertNext(result -> {
                    assertThat(result.successCount()).isZero();
                    assertThat(result.failedCount()).isEqualTo(1);
                    assertThat(result.failed().get(0).error())
                            .contains("has no city")
                            .contains("2 cities");
                })
                .verifyComplete();

        verify(assignmentRepository, never()).save(any());
    }
}
