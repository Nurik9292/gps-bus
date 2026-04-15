package biz.ugur.busroutebackend.advertising.application.usecase.client;

import biz.ugur.busroutebackend.advertising.application.dto.AdTariffResponse;
import biz.ugur.busroutebackend.advertising.application.mapper.AdTariffResponseMapper;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.repository.AdTariffRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class GetActiveTariffsUseCase extends BaseUseCase<String, List<AdTariffResponse>> {

    private final AdTariffRepository tariffRepository;
    private final AdTariffResponseMapper responseMapper;

    public GetActiveTariffsUseCase(AdTariffRepository tariffRepository,
                                    AdTariffResponseMapper responseMapper,
                                    CorrelationContextService correlationService,
                                    EventBus eventBus) {
        super(correlationService, eventBus);
        this.tariffRepository = tariffRepository;
        this.responseMapper = responseMapper;
    }

    @Override
    protected Mono<List<AdTariffResponse>> process(String placementType) {
        var stream = (placementType != null && !placementType.isBlank())
                ? tariffRepository.findActiveByType(PlacementType.from(placementType))
                : tariffRepository.findActive();
        return stream.concatMap(responseMapper::toResponse).collectList();
    }

    @Override
    protected String getBoundContext() { return "advertising.client"; }
}
