package biz.ugur.busroutebackend.admin.application.usecase;

import biz.ugur.busroutebackend.admin.application.dto.city.CityCreateRequest;
import biz.ugur.busroutebackend.admin.application.dto.city.CityResponse;
import biz.ugur.busroutebackend.admin.domain.model.City;
import biz.ugur.busroutebackend.admin.domain.repository.CityRepository;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CreateCityUseCase implements UseCase<CityCreateRequest, Mono<CityResponse>> {

    private final CityRepository cityRepository;

    public CreateCityUseCase(CityRepository cityRepository) {
        this.cityRepository = cityRepository;
    }

    @Override
    public Mono<CityResponse> execute(CityCreateRequest request) {
        log.info("Creating new city: {}", request.getName());

        return cityRepository.existsByName(request.getName())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException("City already exists: " + request.getName()));
                    }

                    City city = new City(
                            request.getName(),
                            request.getNameTm(),
                            request.getDisplayOrder()
                    );

                    return cityRepository.save(city);
                })
                .map(this::toResponse)
                .doOnSuccess(response -> log.info("City created successfully: {}", response.getName()))
                .doOnError(error -> log.error("Failed to create city: {}", request.getName(), error));
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
