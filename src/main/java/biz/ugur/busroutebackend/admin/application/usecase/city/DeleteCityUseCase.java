package biz.ugur.busroutebackend.admin.application.usecase.city;

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
public class DeleteCityUseCase extends BaseUseCase<Mono<String>, Void> {

    private final CityRepository cityRepository;

    public DeleteCityUseCase(CityRepository cityRepository,
                             CorrelationContextService correlationService,
                             EventBus eventBus) {
        super(correlationService, eventBus);
        this.cityRepository = cityRepository;
    }


    @Override
    protected Mono<Void> process(Mono<String> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<Void> processInternal(String cityId) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Deleting city  Correlation {}  ID: {}", correlationId,  cityId);

            return cityRepository.findById(CityId.of(cityId))
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("City not found with ID: " + cityId)))
                    .flatMap(city -> {
                        return cityRepository.deleteById(city.getId());
                    })
                    .then()
                    .doOnSuccess(v -> log.info("City deleted successfully with ID: {}", cityId))
                    .doOnError(error -> log.error("Failed to delete city with ID: {}", cityId, error));
        });
    }
}