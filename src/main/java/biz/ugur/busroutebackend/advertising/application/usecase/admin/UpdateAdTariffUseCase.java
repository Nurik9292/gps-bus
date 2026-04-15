package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.application.dto.AdTariffResponse;
import biz.ugur.busroutebackend.advertising.application.dto.UpdateAdTariffCommand;
import biz.ugur.busroutebackend.advertising.application.mapper.AdTariffResponseMapper;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdTariffNotFoundException;
import biz.ugur.busroutebackend.advertising.domain.repository.AdTariffRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.Price;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffId;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffName;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class UpdateAdTariffUseCase
        extends BaseUseCase<UpdateAdTariffUseCase.Request, AdTariffResponse> {

    public record Request(String tariffId, UpdateAdTariffCommand command) {}

    private final AdTariffRepository tariffRepository;
    private final AdTariffResponseMapper responseMapper;

    public UpdateAdTariffUseCase(AdTariffRepository tariffRepository,
                                  AdTariffResponseMapper responseMapper,
                                  CorrelationContextService correlationService,
                                  EventBus eventBus) {
        super(correlationService, eventBus);
        this.tariffRepository = tariffRepository;
        this.responseMapper = responseMapper;
    }

    @Override
    protected Mono<AdTariffResponse> process(Request req) {
        UpdateAdTariffCommand cmd = req.command();
        return tariffRepository.findById(TariffId.of(req.tariffId()))
                .switchIfEmpty(Mono.error(new AdTariffNotFoundException(req.tariffId())))
                .map(tariff -> tariff.update(
                        cmd.name() != null ? TariffName.of(cmd.name()) : null,
                        cmd.description(),
                        (cmd.priceAmount() != null)
                                ? Price.ofMinor(cmd.priceAmount(),
                                        cmd.currency() != null ? cmd.currency()
                                                : tariff.getPrice().getCurrency())
                                : null,
                        cmd.maxImpressions(),
                        cmd.maxClicks(),
                        cmd.dailyImpressionCap(),
                        cmd.displayOrder()))
                .flatMap(tariffRepository::save)
                .flatMap(responseMapper::toResponse)
                .doOnSuccess(r -> log.info("AdTariff updated: id={}", r.id()));
    }

    @Override
    protected String getBoundContext() { return "advertising.admin"; }
}
