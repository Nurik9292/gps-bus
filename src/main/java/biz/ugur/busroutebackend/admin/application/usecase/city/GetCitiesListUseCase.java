package biz.ugur.busroutebackend.admin.application.usecase.city;

import biz.ugur.busroutebackend.admin.application.dto.city.CityResult;
import biz.ugur.busroutebackend.admin.domain.model.City;
import biz.ugur.busroutebackend.admin.domain.repository.CityRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
public class GetCitiesListUseCase extends BaseUseCase<Mono<Boolean>, List<CityResult>> {

    private final CityRepository cityRepository;

    public GetCitiesListUseCase(
        CityRepository cityRepository,
        CorrelationContextService correlationService,
        EventBus eventBus
    ) {
        super(correlationService, eventBus);
        this.cityRepository = cityRepository;
    }

    @Override
    protected Mono<List<CityResult>> process(Mono<Boolean> activeOnlyRequest) {
        return activeOnlyRequest.flatMap(activeOnly -> {
            log.debug("Getting cities list with activeOnly={}", activeOnly);

            Flux<City> citiesFlux = activeOnly
                ? cityRepository.findActiveCities()
                : cityRepository.findAll();

            return citiesFlux
                .map(CityResult::fromDomain)
                .collectList()
                .map(cities -> cities.stream()
                    .sorted(Comparator
                        .comparing(CityResult::displayOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CityResult::name))
                    .toList())
                .doOnSuccess(list ->
                    log.debug("Retrieved {} cities for list (activeOnly={})", list.size(), activeOnly)
                );
        });
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }
}
