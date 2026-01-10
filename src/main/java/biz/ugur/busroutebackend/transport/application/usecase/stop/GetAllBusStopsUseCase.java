package biz.ugur.busroutebackend.transport.application.usecase.stop;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.transport.application.dto.stop.GetAllStopPaginationQuery;
import biz.ugur.busroutebackend.transport.application.dto.stop.StopList;
import biz.ugur.busroutebackend.transport.application.dto.stop.StopData;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.specification.BusStopSpecifications;
import biz.ugur.busroutebackend.shared.application.util.PaginationHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;


@Service
@Slf4j
public class GetAllBusStopsUseCase extends BaseUseCase<Mono<GetAllStopPaginationQuery>, StopList> {

    private final BusStopRepository busStopRepository;

    public GetAllBusStopsUseCase(BusStopRepository busStopRepository,
                                 CorrelationContextService correlationService,
                                 EventBus eventBus) {
        super(correlationService, eventBus);
        this.busStopRepository = busStopRepository;
    }

    @Override
    protected Mono<StopList> process(Mono<GetAllStopPaginationQuery> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<StopList> processInternal(GetAllStopPaginationQuery query) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.debug("Getting stops with pagination Correlation - {}: page={}, size={}, sort={}, order={}, active={}, search={}",
                    correlationId, query.page(), query.size(), query.sortField(), query.sortOrder(), query.isActivate(), query.searchQuery());

            Pageable pageable = createPageable(query);
            Specification<BusStop> specification = buildSpecification(query);

            Mono<List<BusStop>> stopsMono = (specification != null)
                    ? busStopRepository.findBySpecification(specification, pageable).collectList()
                    : busStopRepository.findAll(pageable).collectList();

            Mono<Long> totalCountMono = (specification != null)
                    ? busStopRepository.countBySpecification(specification)
                    : busStopRepository.count();

            return stopsMono
                    .zipWith(busStopRepository.countActiveStops())
                    .zipWith(totalCountMono)
                    .map(tuple -> {
                        List<BusStop> busStops = tuple.getT1().getT1();
                        Long activeCount = tuple.getT1().getT2();
                        Long totalCount = tuple.getT2();

                        List<StopData> stopLists = busStops.stream()
                                .map(StopData::fromDomain)
                                .toList();

                        return new StopList(
                                stopLists,
                                activeCount,
                                query.page(),
                                query.size(),
                                totalCount
                        );
                    }).doOnSuccess(response -> log.debug(
                            "Retrieved {} stops on page {} of {} ({} active, {} total)",
                            response.getStops().size(),
                            response.getPagination().getCurrentPage(),
                            response.getPagination().getTotalPages(),
                            response.getActiveCount(),
                            response.getPagination().getTotalItems()
                    ));
        });
    }

    private Specification<BusStop> buildSpecification(GetAllStopPaginationQuery query) {
        Specification<BusStop> spec = null;

        if (query.searchQuery() != null && !query.searchQuery().isBlank()) {
            spec = BusStopSpecifications.nameContains(query.searchQuery());
        }

        if (query.isActivate() != null) {
            Specification<BusStop> activeSpec = query.isActivate()
                    ? BusStopSpecifications.isActive()
                    : BusStopSpecifications.isInactive();

            spec = (spec == null) ? activeSpec : spec.and(activeSpec);
        }

        if (query.cityId() != null && !query.cityId().isBlank()) {
            Specification<BusStop> citySpec = BusStopSpecifications.servesCity(query.cityId());
            spec = (spec == null) ? citySpec : spec.and(citySpec);
        }

        return spec;
    }

    private Pageable createPageable(GetAllStopPaginationQuery query) {
        return PaginationHelper.createPageable(
                query.page(), query.size(), query.sortField(), query.sortOrder()
        );
    }
}