package biz.ugur.busroutebackend.admin.application.usecase.city;

import biz.ugur.busroutebackend.admin.application.dto.city.CityResult;
import biz.ugur.busroutebackend.admin.application.dto.city.CreateCity;
import biz.ugur.busroutebackend.admin.domain.model.City;
import biz.ugur.busroutebackend.admin.domain.repository.CityRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CreateCityUseCase extends BaseUseCase<Mono<CreateCity>, CityResult> {

    private final CityRepository cityRepository;

    public CreateCityUseCase(CityRepository cityRepository,
                             CorrelationContextService correlationService,
                             EventBus eventBus) {
        super(correlationService, eventBus);
        this.cityRepository = cityRepository;
    }



    @Override
    protected Mono<CityResult> process(Mono<CreateCity> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<CityResult> processInternal(CreateCity create) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Creating new CorrelationId {} city: {}", correlationId, create.name());
            return cityRepository.existsByName(create.name())
                    .flatMap(exists -> {
                        if (exists) {
                            return Mono.error(new IllegalArgumentException("City already exists: " + create.name()));
                        }

                        City city = new City(
                                create.name(),
                                create.nameTm(),
                                create.displayOrder()
                        );

                        return cityRepository.save(city);
                    })
                    .map(CityResult::fromDomain)
                    .doOnSuccess(response -> log.info("City created successfully: {}", response.name()))
                    .doOnError(error -> log.error("Failed to create city: {}", create.name(), error));
        });
    }
}
