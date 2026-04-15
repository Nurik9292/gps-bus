package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.application.dto.AdTariffResponse;
import biz.ugur.busroutebackend.advertising.application.mapper.AdTariffResponseMapper;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdTariffNotFoundException;
import biz.ugur.busroutebackend.advertising.domain.repository.AdTariffRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class GetAdTariffByIdUseCase extends BaseUseCase<String, AdTariffResponse> {

    private final AdTariffRepository tariffRepository;
    private final AdTariffResponseMapper responseMapper;

    public GetAdTariffByIdUseCase(AdTariffRepository tariffRepository,
                                   AdTariffResponseMapper responseMapper,
                                   CorrelationContextService correlationService,
                                   EventBus eventBus) {
        super(correlationService, eventBus);
        this.tariffRepository = tariffRepository;
        this.responseMapper = responseMapper;
    }

    @Override
    protected Mono<AdTariffResponse> process(String tariffId) {
        return tariffRepository.findById(TariffId.of(tariffId))
                .switchIfEmpty(Mono.error(new AdTariffNotFoundException(tariffId)))
                .flatMap(responseMapper::toResponse);
    }

    @Override
    protected String getBoundContext() { return "advertising.admin"; }
}
