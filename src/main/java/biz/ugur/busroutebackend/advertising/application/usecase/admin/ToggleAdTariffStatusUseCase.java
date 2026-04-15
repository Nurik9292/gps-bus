package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.application.dto.AdTariffResponse;
import biz.ugur.busroutebackend.advertising.application.mapper.AdTariffResponseMapper;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdTariffNotFoundException;
import biz.ugur.busroutebackend.advertising.domain.repository.AdTariffRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class ToggleAdTariffStatusUseCase
        extends BaseUseCase<ToggleAdTariffStatusUseCase.Request, AdTariffResponse> {

    public record Request(String tariffId, boolean activate) {}

    private final AdTariffRepository tariffRepository;
    private final AdTariffResponseMapper responseMapper;

    public ToggleAdTariffStatusUseCase(AdTariffRepository tariffRepository,
                                        AdTariffResponseMapper responseMapper,
                                        CorrelationContextService correlationService,
                                        EventBus eventBus) {
        super(correlationService, eventBus);
        this.tariffRepository = tariffRepository;
        this.responseMapper = responseMapper;
    }

    @Override
    protected Mono<AdTariffResponse> process(Request req) {
        return tariffRepository.findById(TariffId.of(req.tariffId()))
                .switchIfEmpty(Mono.error(new AdTariffNotFoundException(req.tariffId())))
                .map(t -> req.activate() ? t.activate() : t.deactivate())
                .flatMap(tariffRepository::save)
                .flatMap(responseMapper::toResponse)
                .doOnSuccess(r -> log.info("AdTariff toggled: id={} active={}",
                        r.id(), req.activate()));
    }

    @Override
    protected String getBoundContext() { return "advertising.admin"; }
}
