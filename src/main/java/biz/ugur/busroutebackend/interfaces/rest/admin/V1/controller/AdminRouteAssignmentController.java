package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.assignment.RouteAssignmentBatchCreateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.assignment.RouteAssignmentCreateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.assignment.RouteAssignmentUpdateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.assignment.BatchCreateResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.assignment.ClearImmediateResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.assignment.ExcelImportResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.assignment.RouteAssignmentListResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.assignment.RouteAssignmentResponse;
import biz.ugur.busroutebackend.shared.application.SecurityContextService;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import biz.ugur.busroutebackend.transport.application.dto.assignment.ImportFromExcelCommand;
import biz.ugur.busroutebackend.transport.application.dto.assignment.ImportJobStatus;
import biz.ugur.busroutebackend.transport.application.usecase.assignment.*;
import biz.ugur.busroutebackend.transport.infrastructure.excel.AsyncExcelImportService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_ROUTE_ASSIGNMENTS;

@Slf4j
@RestController
@RequestMapping(V1_ADMIN_ROUTE_ASSIGNMENTS)
public class AdminRouteAssignmentController extends BaseController {

    private final CreateRouteAssignmentUseCase createUseCase;
    private final GetRouteAssignmentByIdUseCase getByIdUseCase;
    private final GetRouteAssignmentsByVehicleUseCase getByVehicleUseCase;
    private final DeleteRouteAssignmentUseCase deleteUseCase;
    private final GetAllRouteAssignmentsUseCase getAllUseCase;
    private final GetActiveRouteAssignmentsUseCase getActiveUseCase;
    private final UpdateRouteAssignmentUseCase updateUseCase;
    private final BatchCreateRouteAssignmentsUseCase batchCreateUseCase;
    private final ClearImmediateAssignmentUseCase clearImmediateUseCase;
    private final ClearAllImmediateAssignmentsUseCase clearAllImmediateUseCase;
    private final ImportRouteAssignmentsFromExcelUseCase importFromExcelUseCase;
    private final AsyncExcelImportService asyncExcelImportService;
    private final SecurityContextService securityContextService;

    public AdminRouteAssignmentController(
            CreateRouteAssignmentUseCase createUseCase,
            GetRouteAssignmentByIdUseCase getByIdUseCase,
            GetRouteAssignmentsByVehicleUseCase getByVehicleUseCase,
            DeleteRouteAssignmentUseCase deleteUseCase,
            GetAllRouteAssignmentsUseCase getAllUseCase,
            GetActiveRouteAssignmentsUseCase getActiveUseCase,
            UpdateRouteAssignmentUseCase updateUseCase,
            BatchCreateRouteAssignmentsUseCase batchCreateUseCase,
            ClearImmediateAssignmentUseCase clearImmediateUseCase,
            ClearAllImmediateAssignmentsUseCase clearAllImmediateUseCase,
            ImportRouteAssignmentsFromExcelUseCase importFromExcelUseCase,
            AsyncExcelImportService asyncExcelImportService,
            SecurityContextService securityContextService,
            MessageSource messageSource) {
        super(messageSource);
        this.createUseCase = createUseCase;
        this.getByIdUseCase = getByIdUseCase;
        this.getByVehicleUseCase = getByVehicleUseCase;
        this.deleteUseCase = deleteUseCase;
        this.getAllUseCase = getAllUseCase;
        this.getActiveUseCase = getActiveUseCase;
        this.updateUseCase = updateUseCase;
        this.batchCreateUseCase = batchCreateUseCase;
        this.clearImmediateUseCase = clearImmediateUseCase;
        this.clearAllImmediateUseCase = clearAllImmediateUseCase;
        this.importFromExcelUseCase = importFromExcelUseCase;
        this.asyncExcelImportService = asyncExcelImportService;
        this.securityContextService = securityContextService;
    }

    @Override
    protected String getControllerName() {
        return AdminRouteAssignmentController.class.getSimpleName();
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<RouteAssignmentListResponse>>> getAllAssignments() {
        log.debug("Getting all route assignments");

        return ok(Mono.<Void>empty()
                .as(getAllUseCase::execute)
                .map(RouteAssignmentListResponse::fromData));
    }

    @GetMapping("/active")
    public Mono<ResponseEntity<ApiResponse<RouteAssignmentListResponse>>> getActiveAssignments() {
        log.debug("Getting active route assignments");

        return ok(Mono.<Void>empty()
                .as(getActiveUseCase::execute)
                .map(RouteAssignmentListResponse::fromData));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<RouteAssignmentResponse>>> getAssignmentById(
            @PathVariable String id) {
        log.debug("Getting route assignment by ID: {}", id);

        return ok(Mono.just(id)
                .as(getByIdUseCase::execute)
                .map(RouteAssignmentResponse::fromData));
    }

    @GetMapping("/vehicle/{vehicleId}")
    public Mono<ResponseEntity<ApiResponse<RouteAssignmentListResponse>>> getAssignmentsByVehicle(
            @PathVariable String vehicleId) {
        log.debug("Getting route assignments for vehicle: {}", vehicleId);

        return ok(Mono.just(vehicleId)
                .as(getByVehicleUseCase::execute)
                .map(RouteAssignmentListResponse::fromData));
    }

    @PostMapping
    public Mono<ResponseEntity<ApiResponse<RouteAssignmentResponse>>> createAssignment(
            @Valid @RequestBody RouteAssignmentCreateRequest request) {
        log.info("Creating route assignment: vehicle={}, route={}, date={}, shift={}",
                request.vehicleId(), request.routeId(), request.effectiveDate(), request.shiftType());

        return created(Mono.just(request.toCommand())
                .as(createUseCase::execute)
                .map(RouteAssignmentResponse::fromData));
    }

    @PostMapping("/batch")
    public Mono<ResponseEntity<ApiResponse<BatchCreateResponse>>> batchCreateAssignments(
            @Valid @RequestBody RouteAssignmentBatchCreateRequest request) {
        log.info("Batch creating route assignments: vehicles={}, route={}, date={}, shift={}",
                request.vehicleIds().size(), request.routeId(), request.effectiveDate(), request.shiftType());

        return created(Mono.just(request.toCommand())
                .as(batchCreateUseCase::execute)
                .map(BatchCreateResponse::fromResult));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<RouteAssignmentResponse>>> updateAssignment(
            @PathVariable String id,
            @Valid @RequestBody RouteAssignmentUpdateRequest request) {
        log.info("Updating route assignment: id={}", id);

        return ok(Mono.just(request.toCommand(id))
                .as(updateUseCase::execute)
                .map(RouteAssignmentResponse::fromData));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteAssignment(@PathVariable String id) {
        log.info("Deleting route assignment: {}", id);

        return Mono.just(id)
                .as(deleteUseCase::execute)
                .then(noContent());
    }

    @DeleteMapping("/vehicle/{vehicleId}/immediate")
    public Mono<ResponseEntity<Void>> clearImmediateAssignment(@PathVariable String vehicleId) {
        log.info("Clearing immediate assignment for vehicle: {}", vehicleId);

        return Mono.just(vehicleId)
                .as(clearImmediateUseCase::execute)
                .then(noContent());
    }

    @DeleteMapping("/immediate/all")
    public Mono<ResponseEntity<ApiResponse<ClearImmediateResponse>>> clearAllImmediateAssignments() {
        log.info("Clearing all immediate assignments");

        return ok(Mono.<Void>empty()
                .as(clearAllImmediateUseCase::execute)
                .map(ClearImmediateResponse::fromResult));
    }

    @PostMapping(value = "/import/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<ApiResponse<ExcelImportResponse>>> importFromExcel(
            @RequestPart("file") FilePart file) {
        log.info("Importing route assignments from Excel file: {}", file.filename());

        return ok(securityContextService.getCurrentUsername()
                .flatMap(username -> DataBufferUtils.join(file.content())
                        .map(dataBuffer -> {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            DataBufferUtils.release(dataBuffer);
                            return bytes;
                        })
                        .map(bytes -> new ImportFromExcelCommand(bytes, username))
                        .flatMap(command -> Mono.just(command)
                                .as(importFromExcelUseCase::execute))
                        .map(ExcelImportResponse::fromResult)));
    }

    @PostMapping(value = "/import/excel/async", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<ApiResponse<ImportJobStatus>>> importFromExcelAsync(
            @RequestPart("file") FilePart file) {
        log.info("Async import from Excel file: {}", file.filename());

        return securityContextService.getCurrentUsername()
                .flatMap(username -> DataBufferUtils.join(file.content())
                        .map(dataBuffer -> {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            DataBufferUtils.release(dataBuffer);
                            return bytes;
                        })
                        .flatMap(bytes -> asyncExcelImportService.submitJob(bytes, username)))
                .flatMap(jobId -> asyncExcelImportService.getJobStatus(jobId))
                .map(status -> ResponseEntity
                        .status(202)
                        .body(ApiResponse.success(status)));
    }

    @GetMapping("/import/excel/{jobId}/status")
    public Mono<ResponseEntity<ApiResponse<ImportJobStatus>>> getImportStatus(
            @PathVariable String jobId) {
        return asyncExcelImportService.getJobStatus(jobId)
                .map(status -> ResponseEntity.ok(ApiResponse.success(status)))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
