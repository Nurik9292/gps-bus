package biz.ugur.busroutebackend.admin.application.usecase.city;

import biz.ugur.busroutebackend.admin.application.dto.city.CityList;
import biz.ugur.busroutebackend.admin.application.dto.city.CityResult;
import biz.ugur.busroutebackend.admin.application.dto.city.GetAllCitiesInput;
import biz.ugur.busroutebackend.admin.domain.model.City;
import biz.ugur.busroutebackend.admin.domain.repository.CityRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
public class GetAllCitiesUseCase implements UseCase<Mono<GetAllCitiesInput>, Mono<CityList>> {

    private final CityRepository cityRepository;
    private final CorrelationContextService correlationService;

    public GetAllCitiesUseCase(CityRepository cityRepository, CorrelationContextService correlationService) {
        this.cityRepository = cityRepository;
        this.correlationService = correlationService;
    }

    @Override
    public Mono<CityList> execute(Mono<GetAllCitiesInput> input) {
        return correlationService.executeWithCorrelation(input.flatMap(this::executeWithCorrelation), "admin");
    }

    public Mono<CityList> executeWithCorrelation(GetAllCitiesInput input) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.debug("Getting cities with pagination Correlation - {}: page={}, size={}, sort={}, order={}, active={}",
                    correlationId, input.getPage(), input.getSize(), input.getSort(), input.getOrder(), input.getActive());

            Sort.Direction direction = "desc".equalsIgnoreCase(input.getOrder()) ?
                    Sort.Direction.DESC : Sort.Direction.ASC;
            Sort sort = Sort.by(direction, input.getSort());

            PageRequest pageRequest = PageRequest.of(input.getPage() - 1, input.getSize(), sort);

            return cityRepository.findAllPaged(input.getActive(), pageRequest)
                    .collectList()
                    .zipWith(cityRepository.countActiveCities())
                    .map(tuple -> {
                        List<City> cities = tuple.getT1();
                        Long activeCount = tuple.getT2();

                        List<CityResult> cityResults = cities.stream()
                                .map(CityResult::fromDomain)
                                .toList();

                        return new CityList(cityResults, activeCount);
                    })
                    .doOnSuccess(response -> log.debug("Retrieved {} cities ({} active)",
                            response.getCities().size(), response.getActiveCount()));
        });
    }

}