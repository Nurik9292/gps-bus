package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.application.dto.AdTariffList;
import biz.ugur.busroutebackend.advertising.application.mapper.AdTariffResponseMapper;
import biz.ugur.busroutebackend.advertising.domain.repository.AdTariffRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class GetAllAdTariffsUseCase extends BaseUseCase<GetAllAdTariffsUseCase.Query, AdTariffList> {

    public record Query(int page, int size) {}

    private final AdTariffRepository tariffRepository;
    private final AdTariffResponseMapper responseMapper;

    public GetAllAdTariffsUseCase(AdTariffRepository tariffRepository,
                                   AdTariffResponseMapper responseMapper,
                                   CorrelationContextService correlationService,
                                   EventBus eventBus) {
        super(correlationService, eventBus);
        this.tariffRepository = tariffRepository;
        this.responseMapper = responseMapper;
    }

    @Override
    protected Mono<AdTariffList> process(Query q) {
        var pageable = PageRequest.of(q.page() - 1, q.size(),
                Sort.by(Sort.Direction.ASC, "display_order"));

        return tariffRepository.findAll(pageable)
                .concatMap(responseMapper::toResponse)
                .collectList()
                .zipWith(tariffRepository.count())
                .map(tuple -> AdTariffList.of(
                        tuple.getT1(),
                        (long) tuple.getT1().stream().filter(t -> Boolean.TRUE.equals(t.isActive())).count(),
                        q.page(), q.size(), tuple.getT2()));
    }

    @Override
    protected String getBoundContext() { return "advertising.admin"; }
}
