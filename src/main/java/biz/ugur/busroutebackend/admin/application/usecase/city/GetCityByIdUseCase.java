package biz.ugur.busroutebackend.admin.application.usecase.city;

import biz.ugur.busroutebackend.admin.application.dto.city.CityResult;
import biz.ugur.busroutebackend.admin.domain.repository.CityRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.CityId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GetCityByIdUseCase implements UseCase<Mono<String>, Mono<CityResult>> {

    private final CityRepository cityRepository;
    private final CorrelationContextService correlationService;

    public GetCityByIdUseCase(CityRepository cityRepository, CorrelationContextService correlationService) {
        this.cityRepository = cityRepository;
        this.correlationService = correlationService;
    }

    @Override
    public Mono<CityResult> execute(Mono<String> id) {
      return correlationService.executeWithCorrelation(id.flatMap(this::executeWithCorrelation), "admin");
    }


    private Mono<CityResult> executeWithCorrelation(String id) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.debug("Getting city by CorrelationId - {} ID: {}", correlationId,  id);

            return cityRepository.findById(CityId.of(id))
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("City not found with ID: " + id)))
                    .map(CityResult::fromDomain)
                    .doOnSuccess(response -> log.debug("City found: {}", response.name()))
                    .doOnError(error -> log.error("Failed to get city with ID: {}", id, error));


        });
    }

}