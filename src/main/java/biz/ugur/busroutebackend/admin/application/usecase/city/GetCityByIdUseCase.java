package biz.ugur.busroutebackend.admin.application.usecase.city;

import biz.ugur.busroutebackend.admin.application.dto.city.CityResult;
import biz.ugur.busroutebackend.admin.domain.repository.CityRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.CityId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GetCityByIdUseCase extends BaseUseCase<Mono<String>, CityResult> {

    private final CityRepository cityRepository;

    public GetCityByIdUseCase(CityRepository cityRepository,
                              CorrelationContextService correlationService,
                              EventBus eventBus) {
        super(correlationService, eventBus);
        this.cityRepository = cityRepository;
    }


    @Override
    protected Mono<CityResult> process(Mono<String> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }


    private Mono<CityResult> processInternal(String id) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.debug("Getting city by CorrelationId - {} CityID: {}", correlationId,  id);

            return cityRepository.findById(CityId.of(id))
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("City not found with ID: " + id)))
                    .map(CityResult::fromDomain)
                    .doOnSuccess(response -> log.debug("City found: {}", response.name()))
                    .doOnError(error -> log.error("Failed to get city with ID: {}", id, error));


        });
    }

}