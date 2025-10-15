package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;


import biz.ugur.busroutebackend.admin.application.dto.city.CityResult;
import biz.ugur.busroutebackend.admin.application.dto.city.GetAllCitiesInput;
import biz.ugur.busroutebackend.admin.application.usecase.city.*;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.city.CityCreateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.city.CityUpdateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.city.CityListResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.city.CityResponse;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN;

@RestController
@RequestMapping( V1_ADMIN + "/cities")
@CrossOrigin("*")
public class AdminCityController extends BaseController {

    private final CreateCityUseCase createCityUseCase;
    private final GetAllCitiesUseCase getAllCitiesUseCase;
    private final UpdateCityUseCase updateCityUseCase;
    private final DeleteCityUseCase deleteCityUseCase;
    private final GetCityByIdUseCase getCityByIdUseCase;

    public AdminCityController(CreateCityUseCase createCityUseCase,
                               GetAllCitiesUseCase getAllCitiesUseCase,
                               UpdateCityUseCase updateCityUseCase,
                               DeleteCityUseCase deleteCityUseCase,
                               GetCityByIdUseCase getCityByIdUseCase,
                               MessageSource messageSource) {
        super(messageSource);
        this.createCityUseCase = createCityUseCase;
        this.getAllCitiesUseCase = getAllCitiesUseCase;
        this.updateCityUseCase = updateCityUseCase;
        this.deleteCityUseCase = deleteCityUseCase;
        this.getCityByIdUseCase = getCityByIdUseCase;
    }

    @Override
    protected String getControllerName() {
        return AdminCityController.class.getSimpleName();
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<CityListResponse>>> getAllCities(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String order,
            @RequestParam(required = false) Boolean active) {

      return ok(Mono.just(GetAllCitiesInput.fromParams(page, size, camelToSnake(sort), order, active))
              .as(getAllCitiesUseCase::execute)
              .map(CityListResponse::fromResult));
    }


    @PostMapping
    public Mono<ResponseEntity<ApiResponse<CityResponse>>> createCity(@Valid @RequestBody CityCreateRequest request) {

        return created(Mono.just(request.toCommand())
                .as(createCityUseCase::execute)
                .map(this::toBasicCity));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<CityResponse>>> updateCity(@PathVariable String id,
                                                         @Valid @RequestBody CityUpdateRequest request) {

        return ok(Mono.just(request.toCommand(id))
                .as(updateCityUseCase::execute)
                .map(this::toBasicCity));


    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<CityResponse>>> getCityById(@PathVariable String id) {

        return ok(Mono.just(id)
                .as(getCityByIdUseCase::execute)
                .map(this::toBasicCity));
    }


    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteCity(@PathVariable String id) {

        return Mono.just(id)
                .as(deleteCityUseCase::execute)
                .then(noContent());

    }


    private CityResponse toBasicCity(CityResult result) {
        return CityResponse.fromResult(result);
    }




}
