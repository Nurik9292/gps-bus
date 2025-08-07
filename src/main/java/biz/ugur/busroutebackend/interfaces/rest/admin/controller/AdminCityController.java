package biz.ugur.busroutebackend.interfaces.rest.admin.controller;


import biz.ugur.busroutebackend.admin.application.dto.city.CityResult;
import biz.ugur.busroutebackend.admin.application.dto.city.GetAllCitiesInput;
import biz.ugur.busroutebackend.admin.application.usecase.city.*;
import biz.ugur.busroutebackend.interfaces.rest.admin.request.city.CityCreateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.request.city.CityUpdateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.response.city.CityListResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.response.city.CityResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/admin/cities")
@Slf4j
@CrossOrigin("*")
public class AdminCityController {

    private final CreateCityUseCase createCityUseCase;
    private final GetAllCitiesUseCase getAllCitiesUseCase;
    private final UpdateCityUseCase updateCityUseCase;
    private final DeleteCityUseCase deleteCityUseCase;
    private final GetCityByIdUseCase getCityByIdUseCase;

    public AdminCityController(CreateCityUseCase createCityUseCase,
                               GetAllCitiesUseCase getAllCitiesUseCase,
                               UpdateCityUseCase updateCityUseCase,
                               DeleteCityUseCase deleteCityUseCase,
                               GetCityByIdUseCase getCityByIdUseCase) {
        this.createCityUseCase = createCityUseCase;
        this.getAllCitiesUseCase = getAllCitiesUseCase;
        this.updateCityUseCase = updateCityUseCase;
        this.deleteCityUseCase = deleteCityUseCase;
        this.getCityByIdUseCase = getCityByIdUseCase;
    }

    @GetMapping
    public Mono<ResponseEntity<CityListResponse>> getAllCities(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String order,
            @RequestParam(required = false) Boolean active) {

        log.debug("Fetching cities with params: page={}, size={}, sort={}, order={}, active={}",
                page, size, sort, order, active);

        return Mono.just(GetAllCitiesInput.fromParams(page, size, sort, order, active))
                .as(getAllCitiesUseCase::execute)
                .map(CityListResponse::fromResult)
                .doOnNext(response -> log.debug("Retrieved {} cities", response.getTotalCount()))
                .map(ResponseEntity::ok);
    }


    @PostMapping
    public Mono<ResponseEntity<CityResponse>> createCity(@Valid @RequestBody CityCreateRequest request) {
        log.info("Creating city: {}", request.getName());

        return Mono.just(request.toCommand())
                .as(createCityUseCase::execute)
                .map(this::toCityResponseEntity)
                .onErrorReturn(IllegalArgumentException.class,
                        ResponseEntity.badRequest().build())
                .doOnSuccess(response -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.info("City created successfully: {}", request.getName());
                    }
                })
                .doOnError(error -> log.error("Failed to create city: {}", request.getName(), error));

    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<CityResponse>> updateCity(@PathVariable String id,
                                                         @Valid @RequestBody CityUpdateRequest request) {
        log.info("Updating city with ID: {}", id);

        return Mono.just(request.toCommand(id))
                .as(updateCityUseCase::execute)
                .map(this::toCityResponseEntity)
                .doOnSuccess(response -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.info("City updated successfully with ID: {}", id);
                    }
                })
                .doOnError(error -> log.error("Failed to update city with ID: {}", id, error));

    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<CityResponse>> getCityById(@PathVariable String id) {
        log.debug("Getting city by ID: {}", id);

        return Mono.just(id)
                .as(getCityByIdUseCase::execute)
                .map(this::toCityResponseEntity)
                .doOnError(error -> log.error("Failed to get city by ID: {}", id, error));
    }


    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteCity(@PathVariable String id) {
        log.info("Deleting city with ID: {}", id);

        return Mono.just(id)
                .as(deleteCityUseCase::execute)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .doOnSuccess(response -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.info("City deleted successfully with ID: {}", id);
                    }
                })
                .doOnError(error -> log.error("Failed to delete city with ID: {}", id, error));

    }


    private ResponseEntity<CityResponse> toCityResponseEntity(CityResult result) {
        return ResponseEntity.ok().body(CityResponse.fromResult(result));
    }


}
