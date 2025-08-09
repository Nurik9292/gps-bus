package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.application.dto.route.GetAllRoutePaginationQuery;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteList;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteResult;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteGeometry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
public class GetAllBusRoutesUseCase implements UseCase<Mono<GetAllRoutePaginationQuery>, Mono<RouteList>> {

    private final BusRouteRepository busRouteRepository;
    private final CorrelationContextService correlationService;

    public GetAllBusRoutesUseCase(BusRouteRepository busRouteRepository, CorrelationContextService correlationService) {
        this.busRouteRepository = busRouteRepository;
        this.correlationService = correlationService;
    }

    @Override
    public Mono<RouteList> execute(Mono<GetAllRoutePaginationQuery> query) {
       return correlationService.executeWithCorrelation(query.flatMap(this::executeWithCorrelation), "admin");
    }

    private Mono<RouteList> executeWithCorrelation(GetAllRoutePaginationQuery query) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.debug("Getting route with pagination Correlation - {}: page={}, size={}, sort={}, order={}, active={}",
                    correlationId, query.page(), query.size(), query.size(), query.sortField(), query.page());

            Pageable pageable = createPageable(query);

            return busRouteRepository.getRoutesWithPagination(pageable)
                    .collectList()
                    .zipWith(busRouteRepository.countActiveRoutes())
                    .map(tuple -> {
                        List<BusRoute>  busRoutes = tuple.getT1();
                        Long totalCount = tuple.getT2();
                        List<RouteResult> routeResultList = busRoutes.stream()
                                .map(RouteResult::fromDomain)
                                .toList();

                        return new RouteList(routeResultList, totalCount);
                    });
        });
    }

    private Pageable createPageable(GetAllRoutePaginationQuery query) {
        Sort sort = Sort.by(
                query.sortOrder().equalsIgnoreCase("desc") ?
                        Sort.Direction.DESC : Sort.Direction.ASC,
                query.sortField()
        );

        return PageRequest.of(query.page() - 1, query.size(), sort);
    }

}