package biz.ugur.busroutebackend.advertising.application.usecase.client;

import biz.ugur.busroutebackend.advertising.application.dto.RecordClickCommand;
import biz.ugur.busroutebackend.advertising.domain.model.AdClickEvent;
import biz.ugur.busroutebackend.advertising.domain.repository.AdClickEventRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;

@Service
public class RecordClickUseCase extends BaseUseCase<RecordClickCommand, Void> {

    private final AdClickEventRepository eventRepository;
    private final Clock clock;

    public RecordClickUseCase(AdClickEventRepository eventRepository,
                              Clock clock,
                              CorrelationContextService correlationService,
                              EventBus eventBus) {
        super(correlationService, eventBus);
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    @Override
    protected Mono<Void> process(RecordClickCommand command) {
        AdClickEvent event = AdClickEvent.newRecord(
                PlacementId.of(command.placementId()),
                command.targetType(),
                command.targetId(),
                clock
        );
        return eventRepository.save(event);
    }

    @Override
    protected String getBoundContext() {
        return "advertising.client";
    }
}
