package biz.ugur.busroutebackend.admin.application.usecase;

import biz.ugur.busroutebackend.admin.application.dto.city.CityListResponse;
import biz.ugur.busroutebackend.admin.application.dto.city.CityResponse;
import biz.ugur.busroutebackend.admin.domain.model.City;
import biz.ugur.busroutebackend.admin.domain.repository.CityRepository;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GetAllCitiesUseCase implements UseCase<Boolean, Mono<CityListResponse>> {

    private final CityRepository cityRepository;

    public GetAllCitiesUseCase(CityRepository cityRepository) {
        this.cityRepository = cityRepository;
    }

    @Override
    public Mono<CityListResponse> execute(Boolean activeOnly) {
        log.debug("Fetching cities (activeOnly: {})", activeOnly);

        var cityFlux = activeOnly != null && activeOnly ?
                cityRepository.findActiveCities() :
                cityRepository.findAllCities();

        return cityFlux
                .map(this::toResponse)
                .collectList()
                .flatMap(cities -> cityRepository.countActiveCities()
                        .map(activeCount -> new CityListResponse(cities, activeCount)))
                .doOnSuccess(response -> log.debug("Retrieved {} cities ({} active)",
                        response.getCities().size(), response.getActiveCount()));
    }

    private CityResponse toResponse(City city) {
        return new CityResponse(
                city.getId().getValue(),
                city.getName(),
                city.getNameTm(),
                city.getIsActive(),
                city.getDisplayOrder()
        );
    }
}