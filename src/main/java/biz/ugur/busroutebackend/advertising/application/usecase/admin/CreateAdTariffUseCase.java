package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.application.dto.AdTariffResponse;
import biz.ugur.busroutebackend.advertising.application.dto.CreateAdTariffCommand;
import biz.ugur.busroutebackend.advertising.application.factory.AdTariffFactory;
import biz.ugur.busroutebackend.advertising.application.mapper.AdTariffResponseMapper;
import biz.ugur.busroutebackend.advertising.domain.repository.AdTariffRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CreateAdTariffUseCase extends BaseUseCase<Mono<CreateAdTariffCommand>, AdTariffResponse> {

    private final AdTariffRepository tariffRepository;
    private final AdTariffFactory tariffFactory;
    private final AdTariffResponseMapper responseMapper;

    public CreateAdTariffUseCase(AdTariffRepository tariffRepository,
                                  AdTariffFactory tariffFactory,
                                  AdTariffResponseMapper responseMapper,
                                  CorrelationContextService correlationService,
                                  EventBus eventBus) {
        super(correlationService, eventBus);
        this.tariffRepository = tariffRepository;
        this.tariffFactory = tariffFactory;
        this.responseMapper = responseMapper;
    }

    @Override
    protected Mono<AdTariffResponse> process(Mono<CreateAdTariffCommand> request) {
        return request.flatMap(cmd -> tariffFactory.create(cmd)
                .flatMap(tariffRepository::save)
                .flatMap(responseMapper::toResponse)
                .doOnSuccess(r -> log.info("AdTariff created: id={} name={}", r.id(), r.name())));
    }

    @Override
    protected String getBoundContext() { return "advertising.admin"; }
}
