package biz.ugur.busroutebackend.client.application.usecase.city;

import biz.ugur.busroutebackend.admin.domain.repository.CityRepository;
import biz.ugur.busroutebackend.client.application.dto.city.MobileCityResult;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

/**
 * Use case for retrieving active cities for mobile clients.
 * Returns only active cities sorted by display order.
 */
@Service
@Slf4j
public class GetMobileCitiesUseCase extends BaseUseCase<Void, List<MobileCityResult>> {

    private final CityRepository cityRepository;

    public GetMobileCitiesUseCase(CityRepository cityRepository,
                                   CorrelationContextService correlationService,
                                   EventBus eventBus) {
        super(correlationService, eventBus);
        this.cityRepository = cityRepository;
    }

    @Override
    protected Mono<List<MobileCityResult>> process(Void request) {
        return cityRepository.findActiveCities()
                .map(MobileCityResult::fromDomain)
                .sort(Comparator.comparing(MobileCityResult::displayOrder))
                .collectList()
                .doOnSuccess(cities -> log.debug("Retrieved {} active cities for mobile", cities.size()));
    }

    @Override
    protected String getBoundContext() {
        return "client";
    }
}
