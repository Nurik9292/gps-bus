package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.place.AliasCreateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.place.AliasUpdateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.place.PlaceCreateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.place.PlaceUpdateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.place.PlaceAdminResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.place.PlaceListAdminResponse;
import biz.ugur.busroutebackend.place.application.dto.*;
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

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_PLACES;

@RestController
@RequestMapping(V1_ADMIN_PLACES)
public class AdminPlaceController extends BasePaginatedController {

    private final CreatePlaceUseCase createPlaceUseCase;
    private final UpdatePlaceUseCase updatePlaceUseCase;
    private final DeletePlaceUseCase deletePlaceUseCase;
    private final GetAllPlacesUseCase getAllPlacesUseCase;
    private final GetPlaceByIdUseCase getPlaceByIdUseCase;
    private final GetPlaceAliasesUseCase getPlaceAliasesUseCase;
    private final CreatePlaceAliasUseCase createPlaceAliasUseCase;
    private final UpdatePlaceAliasUseCase updatePlaceAliasUseCase;
    private final DeletePlaceAliasUseCase deletePlaceAliasUseCase;
    private final ImportPlacesFromCsvUseCase importPlacesFromCsvUseCase;

    public AdminPlaceController(CreatePlaceUseCase createPlaceUseCase,
                                UpdatePlaceUseCase updatePlaceUseCase,
                                DeletePlaceUseCase deletePlaceUseCase,
                                GetAllPlacesUseCase getAllPlacesUseCase,
                                GetPlaceByIdUseCase getPlaceByIdUseCase,
                                GetPlaceAliasesUseCase getPlaceAliasesUseCase,
                                CreatePlaceAliasUseCase createPlaceAliasUseCase,
                                UpdatePlaceAliasUseCase updatePlaceAliasUseCase,
                                DeletePlaceAliasUseCase deletePlaceAliasUseCase,
                                ImportPlacesFromCsvUseCase importPlacesFromCsvUseCase,
                                MessageSource messageSource) {
        super(messageSource);
        this.createPlaceUseCase = createPlaceUseCase;
        this.updatePlaceUseCase = updatePlaceUseCase;
        this.deletePlaceUseCase = deletePlaceUseCase;
        this.getAllPlacesUseCase = getAllPlacesUseCase;
        this.getPlaceByIdUseCase = getPlaceByIdUseCase;
        this.getPlaceAliasesUseCase = getPlaceAliasesUseCase;
        this.createPlaceAliasUseCase = createPlaceAliasUseCase;
        this.updatePlaceAliasUseCase = updatePlaceAliasUseCase;
        this.deletePlaceAliasUseCase = deletePlaceAliasUseCase;
        this.importPlacesFromCsvUseCase = importPlacesFromCsvUseCase;
    }

    @Override
    protected String getControllerName() {
        return AdminPlaceController.class.getSimpleName();
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<PlaceListAdminResponse>>> getAllPlaces(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String order,
            @RequestParam(required = false) String cityId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search) {

        validatePagination(page, size);

        return ok(Mono.just(GetAllPlacesInput.fromParams(page, size, camelToSnake(sort), order, cityId, category, search))
                .as(getAllPlacesUseCase::execute)
                .map(PlaceListAdminResponse::fromResult));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<PlaceAdminResponse>>> getPlaceById(@PathVariable String id) {
        return ok(Mono.just(id)
                .as(getPlaceByIdUseCase::execute)
                .map(PlaceAdminResponse::fromResult));
    }

    @PostMapping
    public Mono<ResponseEntity<ApiResponse<PlaceAdminResponse>>> createPlace(
            @Valid @RequestBody PlaceCreateRequest request) {
        return created(Mono.just(request.toCommand())
                .as(createPlaceUseCase::execute)
                .map(PlaceAdminResponse::fromResult));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<PlaceAdminResponse>>> updatePlace(
            @PathVariable String id,
            @Valid @RequestBody PlaceUpdateRequest request) {
        return ok(Mono.just(request.toCommand(id))
                .as(updatePlaceUseCase::execute)
                .map(PlaceAdminResponse::fromResult));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deletePlace(@PathVariable String id) {
        return Mono.just(id)
                .as(deletePlaceUseCase::execute)
                .then(noContent());
    }

    @GetMapping("/{placeId}/aliases")
    public Mono<ResponseEntity<ApiResponse<List<AliasResult>>>> getPlaceAliases(@PathVariable String placeId) {
        return ok(Mono.just(placeId).as(getPlaceAliasesUseCase::execute));
    }

    @PostMapping("/{placeId}/aliases")
    public Mono<ResponseEntity<ApiResponse<AliasResult>>> createPlaceAlias(
            @PathVariable String placeId,
            @Valid @RequestBody AliasCreateRequest request) {
        return created(Mono.just(new CreatePlaceAliasInput(placeId, request.getAlias(), request.getLanguage()))
                .as(createPlaceAliasUseCase::execute));
    }

    @PutMapping("/aliases/{aliasId}")
    public Mono<ResponseEntity<ApiResponse<AliasResult>>> updatePlaceAlias(
            @PathVariable String aliasId,
            @Valid @RequestBody AliasUpdateRequest request) {
        return ok(Mono.just(request.toCommand(aliasId))
                .as(updatePlaceAliasUseCase::execute));
    }

    @DeleteMapping("/aliases/{aliasId}")
    public Mono<ResponseEntity<Void>> deletePlaceAlias(@PathVariable String aliasId) {
        return Mono.just(aliasId)
                .as(deletePlaceAliasUseCase::execute)
                .then(noContent());
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<ApiResponse<CsvImportResult>>> importCsv(
            @RequestPart("file") FilePart file,
            @RequestPart(value = "cityId", required = false) String cityId) {
        return ok(Mono.just(new ImportPlacesFromCsvUseCase.Input(file, cityId))
                .as(importPlacesFromCsvUseCase::execute));
    }
}
