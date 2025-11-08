package biz.ugur.busroutebackend.admin.application.usecase.city;

import biz.ugur.busroutebackend.admin.application.dto.city.CityResult;
import biz.ugur.busroutebackend.admin.application.dto.city.CityUpdate;
import biz.ugur.busroutebackend.admin.domain.model.City;
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
public class UpdateCityUseCase extends BaseUseCase<Mono<CityUpdate>, CityResult> {

    private final CityRepository cityRepository;

    public UpdateCityUseCase(CityRepository cityRepository,
                             CorrelationContextService correlationService,
                             EventBus eventBus) {
        super(correlationService, eventBus);
        this.cityRepository = cityRepository;
    }

    @Override
    protected Mono<CityResult> process(Mono<CityUpdate> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }


    private Mono<CityResult> processInternal(CityUpdate update) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Updating city  CorrelationId: {} - CityId: {}", correlationId, update.id());

            return cityRepository.findById(CityId.of(update.id()))
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("City not found with ID: " + update.id())))
                    .flatMap(city -> {
                        // Check if name is changing and if new name already exists
                        if (!city.getName().equals(update.name())) {
                            return cityRepository.existsByNameAndIdNot(update.name(), CityId.of(update.id()))
                                    .flatMap(exists -> {
                                        if (exists) {
                                            return Mono.<City>error(new IllegalArgumentException(
                                                    "City already exists with name: " + update.name()));
                                        }
                                        return Mono.just(city);
                                    });
                        }
                        return Mono.just(city);
                    })
                    .map(city -> {
                        City updatedCity = city.updateCity(update.name(), update.nameTm(), update.displayOrder());

                        if (update.isActive() != null) {
                            if (update.isActive()) {
                                return updatedCity.activate();
                            } else {
                                return updatedCity.deactivate();
                            }
                        }
                        return updatedCity;
                    })
                    .flatMap(cityRepository::save)
                    .map(CityResult::fromDomain)
                    .doOnSuccess(response -> log.info("City updated successfully: {}", response.name()))
                    .doOnError(error -> log.error("Failed to update city with ID: {}", update.id(), error));
        });
    }


}