package biz.ugur.busroutebackend.admin.application.usecase.city;

import biz.ugur.busroutebackend.admin.application.dto.city.CityResult;
import biz.ugur.busroutebackend.admin.application.dto.city.CityUpdate;
import biz.ugur.busroutebackend.admin.domain.model.City;
import biz.ugur.busroutebackend.admin.domain.repository.CityRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.CityId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class UpdateCityUseCase implements UseCase<Mono<CityUpdate>, Mono<CityResult>> {

    private final CityRepository cityRepository;
    private final CorrelationContextService correlationService;

    public UpdateCityUseCase(CityRepository cityRepository, CorrelationContextService correlationService) {
        this.cityRepository = cityRepository;
        this.correlationService = correlationService;
    }

    @Override
    public Mono<CityResult> execute(Mono<CityUpdate> update) {
        return correlationService.executeWithCorrelation(update.flatMap(this::executeWithCorrelation), "admin");
    }


    private Mono<CityResult> executeWithCorrelation(CityUpdate update) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Updating city  CorrelationId: {}  CityId: {}", correlationId, update.id());

            return cityRepository.findById(CityId.of(update.id()))
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("City not found with ID: " + update.id())))
                    .flatMap(city -> {
                        if (!city.getName().equals(update.name())) {
                            return cityRepository.existsByNameAndIdNot(update.name(), CityId.of(update.id()))
                                    .flatMap(exists -> {
                                        if (exists) {
                                            return Mono.<City>error(new IllegalArgumentException(
                                                    "City already exists with name: " + update.name()));
                                        }

                                        city.updateCity(update.name(), update.nameTm(), update.displayOrder());

                                        if(update.isActive())
                                            city.activate();
                                        else city.deactivate();

                                        return cityRepository.save(city).flatMap(Mono::just);
                                    });
                        } else {
                            city.updateCity(update.name(), update.nameTm(), update.displayOrder());
                            if(update.isActive())
                                city.activate();
                            else city.deactivate();
                            return cityRepository.save(city);
                        }
                    })
                    .map(CityResult::fromDomain)
                    .doOnSuccess(response -> log.info("City updated successfully: {}", response.name()))
                    .doOnError(error -> log.error("Failed to update city with ID: {}", update.id(), error));
        });
    }


}