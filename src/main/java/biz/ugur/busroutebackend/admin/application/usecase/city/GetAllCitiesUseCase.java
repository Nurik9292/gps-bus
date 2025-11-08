package biz.ugur.busroutebackend.admin.application.usecase.city;

import biz.ugur.busroutebackend.admin.application.dto.city.CityList;
import biz.ugur.busroutebackend.admin.application.dto.city.CityResult;
import biz.ugur.busroutebackend.admin.application.dto.city.GetAllCitiesInput;
import biz.ugur.busroutebackend.admin.domain.model.City;
import biz.ugur.busroutebackend.admin.domain.repository.CityRepository;
import biz.ugur.busroutebackend.admin.domain.specification.CitySpecifications;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
public class GetAllCitiesUseCase extends BaseUseCase<Mono<GetAllCitiesInput>, CityList> {

    private final CityRepository cityRepository;

    public GetAllCitiesUseCase(CityRepository cityRepository,
                               CorrelationContextService correlationService,
                               EventBus eventBus) {
        super(correlationService, eventBus);
        this.cityRepository = cityRepository;
    }


    @Override
    protected Mono<CityList> process(Mono<GetAllCitiesInput> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    public Mono<CityList> processInternal(GetAllCitiesInput input) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.debug("Getting cities with pagination Correlation - {}: page: {}, size: {}, sort: {}, order: {}, active: {}",
                    correlationId, input.getPage(), input.getSize(), input.getSort(), input.getOrder(), input.getActive());

            Pageable pageRequest = createPageable(input);
            Specification<City> specification = buildSpecification(input);

            Mono<List<City>> citiesMono = (specification != null)
                    ? cityRepository.findBySpecification(specification, pageRequest).collectList()
                    : cityRepository.findAll(pageRequest).collectList();

            Mono<Long> totalCountMono = (specification != null)
                    ? cityRepository.countBySpecification(specification)
                    : cityRepository.count();

            return citiesMono
                    .zipWith(cityRepository.countActiveCities())
                    .zipWith(totalCountMono)
                    .map(tuple -> {
                        List<City> cities = tuple.getT1().getT1();
                        Long totalCount = tuple.getT1().getT2();
                        Long activeCount = tuple.getT2();

                        List<CityResult> cityResults = cities.stream()
                                .map(CityResult::fromDomain)
                                .toList();

                        return new CityList(
                            cityResults,
                            activeCount,
                            input.getPage(),
                            input.getSize(),
                            totalCount
                        );
                    })
                    .doOnSuccess(response -> log.debug("Retrieved {} cities out of {} total ({} active)",
                            response.getCities().size(),
                            response.getPagination().getTotalItems(),
                            response.getActiveCount()));
        });
    }



    private Specification<City> buildSpecification(GetAllCitiesInput query) {
        Specification<City> spec = null;

        if (query.getSearch() != null && !query.getSearch().isBlank()) {
            spec = CitySpecifications.nameContains(query.getSearch());
        }

        if (query.getActive() != null) {
            Specification<City> activeSpec = query.getActive()
                    ? CitySpecifications.isActive()
                    : CitySpecifications.isInactive();

            spec = (spec == null) ? activeSpec : spec.and(activeSpec);
        }

        return spec;
    }

    private Pageable createPageable(GetAllCitiesInput input) {
        Sort.Direction direction = "desc".equalsIgnoreCase(input.getOrder()) ?
                Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, input.getSort());

        return PageRequest.of(input.getPage() - 1, input.getSize(), sort);
    }
}