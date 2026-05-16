package biz.ugur.busroutebackend.advertising.application.usecase.client;

import biz.ugur.busroutebackend.advertising.application.dto.RecordDetailViewCommand;
import biz.ugur.busroutebackend.advertising.domain.model.AdDetailViewEvent;
import biz.ugur.busroutebackend.advertising.domain.repository.AdDetailViewEventRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;

@Service
public class RecordDetailViewUseCase extends BaseUseCase<RecordDetailViewCommand, Void> {

    private final AdDetailViewEventRepository eventRepository;
    private final Clock clock;

    public RecordDetailViewUseCase(AdDetailViewEventRepository eventRepository,
                                   Clock clock,
                                   CorrelationContextService correlationService,
                                   EventBus eventBus) {
        super(correlationService, eventBus);
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    @Override
    protected Mono<Void> process(RecordDetailViewCommand command) {
        return Mono.fromCallable(() -> AdDetailViewEvent.newRecord(
                        PlacementId.of(command.placementId()),
                        command.durationMs(),
                        command.targetType(),
                        command.targetId(),
                        clock
                ))
                .flatMap(eventRepository::save);
    }

    @Override
    protected String getBoundContext() {
        return "advertising.client";
    }
}
