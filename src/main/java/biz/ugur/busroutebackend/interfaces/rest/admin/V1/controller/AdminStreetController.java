package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.place.AliasCreateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.place.AliasUpdateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.place.StreetCreateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.place.StreetUpdateRequest;
import biz.ugur.busroutebackend.place.application.dto.CsvImportResult;
import biz.ugur.busroutebackend.place.application.dto.StreetAliasResult;
import biz.ugur.busroutebackend.place.application.dto.StreetList;
import biz.ugur.busroutebackend.place.application.dto.StreetResult;
import biz.ugur.busroutebackend.place.application.dto.UpdateAliasCommand;
import biz.ugur.busroutebackend.place.application.usecase.*;
import biz.ugur.busroutebackend.shared.infrastructure.web.BasePaginatedController;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_STREETS;

@RestController
@RequestMapping(V1_ADMIN_STREETS)
public class AdminStreetController extends BasePaginatedController {

    private final CreateStreetUseCase createStreetUseCase;
    private final UpdateStreetUseCase updateStreetUseCase;
    private final DeleteStreetUseCase deleteStreetUseCase;
    private final GetAllStreetsUseCase getAllStreetsUseCase;
    private final GetStreetAliasesUseCase getStreetAliasesUseCase;
    private final CreateStreetAliasUseCase createStreetAliasUseCase;
    private final UpdateStreetAliasUseCase updateStreetAliasUseCase;
    private final DeleteStreetAliasUseCase deleteStreetAliasUseCase;
    private final ImportStreetsFromCsvUseCase importStreetsFromCsvUseCase;

    public AdminStreetController(CreateStreetUseCase createStreetUseCase,
                                 UpdateStreetUseCase updateStreetUseCase,
                                 DeleteStreetUseCase deleteStreetUseCase,
                                 GetAllStreetsUseCase getAllStreetsUseCase,
                                 GetStreetAliasesUseCase getStreetAliasesUseCase,
                                 CreateStreetAliasUseCase createStreetAliasUseCase,
                                 UpdateStreetAliasUseCase updateStreetAliasUseCase,
                                 DeleteStreetAliasUseCase deleteStreetAliasUseCase,
                                 ImportStreetsFromCsvUseCase importStreetsFromCsvUseCase,
                                 MessageSource messageSource) {
        super(messageSource);
        this.createStreetUseCase = createStreetUseCase;
        this.updateStreetUseCase = updateStreetUseCase;
        this.deleteStreetUseCase = deleteStreetUseCase;
        this.getAllStreetsUseCase = getAllStreetsUseCase;
        this.getStreetAliasesUseCase = getStreetAliasesUseCase;
        this.createStreetAliasUseCase = createStreetAliasUseCase;
        this.updateStreetAliasUseCase = updateStreetAliasUseCase;
        this.deleteStreetAliasUseCase = deleteStreetAliasUseCase;
        this.importStreetsFromCsvUseCase = importStreetsFromCsvUseCase;
    }

    @Override
    protected String getControllerName() {
        return AdminStreetController.class.getSimpleName();
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<StreetList>>> getAllStreets(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String order,
            @RequestParam(required = false) String cityId) {

        validatePagination(page, size);

        return ok(Mono.just(GetAllStreetsUseCase.Input.fromParams(page, size, camelToSnake(sort), order, cityId))
                .as(getAllStreetsUseCase::execute));
    }

    @PostMapping
    public Mono<ResponseEntity<ApiResponse<StreetResult>>> createStreet(
            @Valid @RequestBody StreetCreateRequest request) {
        return created(Mono.just(request.toCommand())
                .as(createStreetUseCase::execute));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<StreetResult>>> updateStreet(
            @PathVariable String id,
            @Valid @RequestBody StreetUpdateRequest request) {
        return ok(Mono.just(request.toCommand(id))
                .as(updateStreetUseCase::execute));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteStreet(@PathVariable String id) {
        return Mono.just(id)
                .as(deleteStreetUseCase::execute)
                .then(noContent());
    }

    @GetMapping("/{streetId}/aliases")
    public Mono<ResponseEntity<ApiResponse<List<StreetAliasResult>>>> getStreetAliases(
            @PathVariable String streetId) {
        return ok(Mono.just(streetId).as(getStreetAliasesUseCase::execute));
    }

    @PostMapping("/{streetId}/aliases")
    public Mono<ResponseEntity<ApiResponse<StreetAliasResult>>> createStreetAlias(
            @PathVariable String streetId,
            @Valid @RequestBody AliasCreateRequest request) {
        return created(Mono.just(new CreateStreetAliasUseCase.Input(streetId, request.getAlias(), request.getLanguage()))
                .as(createStreetAliasUseCase::execute));
    }

    @PutMapping("/aliases/{aliasId}")
    public Mono<ResponseEntity<ApiResponse<StreetAliasResult>>> updateStreetAlias(
            @PathVariable String aliasId,
            @Valid @RequestBody AliasUpdateRequest request) {
        return ok(Mono.just(new UpdateAliasCommand(aliasId, request.getAlias(), request.getLanguage()))
                .as(updateStreetAliasUseCase::execute));
    }

    @DeleteMapping("/aliases/{aliasId}")
    public Mono<ResponseEntity<Void>> deleteStreetAlias(@PathVariable String aliasId) {
        return Mono.just(aliasId)
                .as(deleteStreetAliasUseCase::execute)
                .then(noContent());
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<ApiResponse<CsvImportResult>>> importCsv(
            @RequestPart("file") FilePart file,
            @RequestPart(value = "cityId", required = false) String cityId) {
        return ok(Mono.just(new ImportStreetsFromCsvUseCase.Input(file, cityId))
                .as(importStreetsFromCsvUseCase::execute));
    }
}
