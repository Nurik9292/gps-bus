package biz.ugur.busroutebackend.transport.application.usecase.assignment;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.assignment.*;
import biz.ugur.busroutebackend.transport.application.mapper.RouteAssignmentDataMapper;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteSource;
import biz.ugur.busroutebackend.transport.infrastructure.excel.ExcelRouteAssignmentParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ImportRouteAssignmentsFromExcelUseCase extends BaseUseCase<Mono<ImportFromExcelCommand>, ExcelImportResult> {

    private static final ZoneId ASHGABAT_ZONE = ZoneId.of("Asia/Ashgabat");

    private final ExcelRouteAssignmentParser excelParser;
    private final RouteAssignmentRepository assignmentRepository;
    private final VehicleRepository vehicleRepository;
    private final BusRouteRepository busRouteRepository;
    private final RouteAssignmentDataMapper dataMapper;

    public ImportRouteAssignmentsFromExcelUseCase(
            CorrelationContextService correlationService,
            EventBus eventBus,
            ExcelRouteAssignmentParser excelParser,
            RouteAssignmentRepository assignmentRepository,
            VehicleRepository vehicleRepository,
            BusRouteRepository busRouteRepository,
            RouteAssignmentDataMapper dataMapper) {
        super(correlationService, eventBus);
        this.excelParser = excelParser;
        this.assignmentRepository = assignmentRepository;
        this.vehicleRepository = vehicleRepository;
        this.busRouteRepository = busRouteRepository;
        this.dataMapper = dataMapper;
    }

    @Override
    protected Mono<ExcelImportResult> process(Mono<ImportFromExcelCommand> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<ExcelImportResult> processInternal(ImportFromExcelCommand command) {
        log.info("Starting Excel import for route assignments");

        return excelParser.parse(command.fileContent())
                .flatMap(rows -> processRows(rows, command.assignedBy()));
    }

    private Mono<ExcelImportResult> processRows(List<ExcelImportRow> rows, String assignedBy) {
        List<RouteAssignmentData> created = new ArrayList<>();
        List<ExcelImportResult.FailedImportRow> failed = new ArrayList<>();

        List<ExcelImportRow> validRows = rows.stream()
                .filter(row -> {
                    if (row.shiftType() != null && row.shiftType().startsWith("PARSE_ERROR:")) {
                        failed.add(new ExcelImportResult.FailedImportRow(
                                row.rowNumber(),
                                row.licensePlate(),
                                row.shiftType().replace("PARSE_ERROR: ", "")
                        ));
                        return false;
                    }
                    return true;
                })
                .toList();

        log.info("Processing {} valid rows out of {} total", validRows.size(), rows.size());

        return Flux.fromIterable(validRows)
                .concatMap(row -> processRow(row, assignedBy)
                        .flatMap(assignment -> dataMapper.toRouteAssignmentData(assignment)
                                .doOnSuccess(created::add))
                        .onErrorResume(error -> {
                            log.warn("Failed to create assignment for row {}: {}",
                                    row.rowNumber(), error.getMessage());
                            failed.add(new ExcelImportResult.FailedImportRow(
                                    row.rowNumber(),
                                    row.licensePlate(),
                                    error.getMessage()
                            ));
                            return Mono.empty();
                        }))
                .collectList()
                .map(results -> new ExcelImportResult(
                        created,
                        failed,
                        created.size(),
                        failed.size(),
                        rows.size()
                ))
                .doOnSuccess(result -> log.info("Excel import completed: success={}, failed={}, total={}",
                        result.successCount(), result.failedCount(), result.totalRows()));
    }

    private Mono<RouteAssignment> processRow(ExcelImportRow row, String assignedBy) {
        return findVehicleByLicensePlate(row.licensePlate())
                .flatMap(vehicle -> findRouteByNumber(row.routeNumber())
                        .flatMap(route -> createAssignment(vehicle, route, row, assignedBy)));
    }

    private Mono<Vehicle> findVehicleByLicensePlate(String licensePlate) {
        return vehicleRepository.findByLicensePlate(licensePlate)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Vehicle not found: " + licensePlate)));
    }

    private Mono<BusRoute> findRouteByNumber(String routeNumber) {
        return busRouteRepository.findByRouteNumber(routeNumber)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Route not found: " + routeNumber)));
    }

    private Mono<RouteAssignment> createAssignment(Vehicle vehicle, BusRoute route,
                                                     ExcelImportRow row, String assignedBy) {
        ShiftType shiftType = ShiftType.fromString(row.shiftType());

        return checkNoConflictingAssignment(vehicle, row, shiftType)
                .then(Mono.defer(() -> {
                    Instant expiresAt = calculateExpiresAt(row);

                    RouteAssignment assignment = RouteAssignment.create(
                            vehicle.getId(),
                            route.getId(),
                            row.effectiveDate(),
                            shiftType,
                            assignedBy,
                            null,
                            expiresAt
                    );
                    return assignmentRepository.save(assignment);
                }))
                .flatMap(assignment -> applyImmediatelyIfCurrentShift(assignment, vehicle, route)
                        .thenReturn(assignment));
    }

    private Mono<Void> checkNoConflictingAssignment(Vehicle vehicle, ExcelImportRow row, ShiftType shiftType) {
        return assignmentRepository.existsActiveByVehicleAndDateAndShift(
                        vehicle.getId(), row.effectiveDate(), shiftType)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException(
                                "Vehicle already has assignment for this date and shift"));
                    }
                    return Mono.empty();
                });
    }

    private Instant calculateExpiresAt(ExcelImportRow row) {
        LocalTime expiresTime = row.expiresTime();
        if (expiresTime == null) {
            ShiftType shiftType = ShiftType.fromString(row.shiftType());
            expiresTime = shiftType.getEndTime();
        }

        ZonedDateTime expiresDateTime = row.effectiveDate()
                .atTime(expiresTime)
                .atZone(ASHGABAT_ZONE);

        return expiresDateTime.toInstant();
    }

    private Mono<Void> applyImmediatelyIfCurrentShift(RouteAssignment assignment,
                                                        Vehicle vehicle, BusRoute route) {
        if (!assignment.isForCurrentShift()) {
            return Mono.empty();
        }

        if (!Boolean.TRUE.equals(vehicle.getIsActive())) {
            return Mono.empty();
        }

        Vehicle updatedVehicle = vehicle.toBuilder()
                .assignedRouteId(assignment.getRouteId())
                .routeNumber(route.getRouteNumber())
                .routeSource(RouteSource.SHIFT_ASSIGNMENT)
                .routeConfidence(100)
                .build();

        return vehicleRepository.save(updatedVehicle)
                .doOnSuccess(v -> log.info("Applied assignment immediately: vehicle={}, route={}",
                        v.getLicensePlate(), v.getRouteNumber()))
                .then()
                .onErrorResume(error -> {
                    log.warn("Failed to apply assignment immediately: {}", error.getMessage());
                    return Mono.empty();
                });
    }
}
