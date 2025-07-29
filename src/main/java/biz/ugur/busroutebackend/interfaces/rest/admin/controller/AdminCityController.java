package biz.ugur.busroutebackend.interfaces.rest.admin.controller;


import biz.ugur.busroutebackend.admin.application.dto.city.CityCreateRequest;
import biz.ugur.busroutebackend.admin.application.dto.city.CityListResponse;
import biz.ugur.busroutebackend.admin.application.dto.city.CityResponse;
import biz.ugur.busroutebackend.admin.application.usecase.CreateCityUseCase;
import biz.ugur.busroutebackend.admin.application.usecase.GetAllCitiesUseCase;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/admin/cities")
@Slf4j
@CrossOrigin(origins = "*")
public class AdminCityController {

    private final CreateCityUseCase createCityUseCase;
    private final GetAllCitiesUseCase getAllCitiesUseCase;

    public AdminCityController(CreateCityUseCase createCityUseCase,
                               GetAllCitiesUseCase getAllCitiesUseCase) {
        this.createCityUseCase = createCityUseCase;
        this.getAllCitiesUseCase = getAllCitiesUseCase;
    }

    @PostMapping
    public Mono<ResponseEntity<CityResponse>> createCity(@Valid @RequestBody CityCreateRequest request) {
        log.info("Creating city: {}", request.getName());

        return createCityUseCase.execute(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response))
                .onErrorReturn(IllegalArgumentException.class,
                        ResponseEntity.badRequest().build())
                .doOnSuccess(response -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.info("City created successfully: {}", request.getName());
                    }
                })
                .doOnError(error -> log.error("Failed to create city: {}", request.getName(), error));
    }

    @GetMapping
    public Mono<ResponseEntity<CityListResponse>> getAllCities(
            @RequestParam(required = false) Boolean active) {

        log.debug("Fetching cities (active: {})", active);

        return getAllCitiesUseCase.execute(active)
                .map(ResponseEntity::ok)
                .doOnSuccess(response -> {
                    if (response.getBody() != null) {
                        log.debug("Retrieved {} cities", response.getBody().getTotalCount());
                    }
                });
    }
}
