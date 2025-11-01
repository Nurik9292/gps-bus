package biz.ugur.busroutebackend.transport.application.usecase.stop;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.stop.GetAllStopPaginationQuery;
import biz.ugur.busroutebackend.transport.application.dto.stop.StopList;
import biz.ugur.busroutebackend.transport.application.dto.stop.StopData;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
            log.debug("Getting stops with pagination Correlation - {}: page={}, size={}, sort={}, order={}, active={}",
                    correlationId, query.page(), query.size(), query.sortField(), query.sortOrder(), query.isActivate());

            Pageable pageable = createPageable(query);

            return busStopRepository.findAll(pageable)
                    .collectList()
                    .zipWith(busStopRepository.countActiveStops())
                    .map(tuple -> {
                        List<BusStop> busStops = tuple.getT1();
                        Long activeCount = tuple.getT2();
                        List<StopData> stopLists = busStops.stream()
                                .map(StopData::fromDomain)
                                .toList();

                        return new StopList(stopLists, activeCount);
                    }).doOnSuccess(response -> log.debug("Retrieved {} stops ({} active)",
                            response.getStops().size(), response.getActiveCount()));
        });
    }


    private Pageable createPageable(GetAllStopPaginationQuery query) {
        Sort sort = Sort.by(
                query.sortOrder().equalsIgnoreCase("desc") ?
                        Sort.Direction.DESC : Sort.Direction.ASC,
                query.sortField()
        );

        return PageRequest.of(query.page() - 1, query.size(), sort);
    }


}