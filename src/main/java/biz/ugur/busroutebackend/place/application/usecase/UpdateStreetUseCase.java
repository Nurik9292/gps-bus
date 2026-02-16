package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.StreetResult;
import biz.ugur.busroutebackend.place.application.dto.UpdateStreetCommand;
import biz.ugur.busroutebackend.place.domain.exceptions.PlaceNotFoundException;
import biz.ugur.busroutebackend.place.domain.repository.StreetRepository;
import biz.ugur.busroutebackend.place.domain.valueobjects.StreetId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class UpdateStreetUseCase extends BaseUseCase<Mono<UpdateStreetCommand>, StreetResult> {

    private final StreetRepository streetRepository;

    public UpdateStreetUseCase(StreetRepository streetRepository,
                               CorrelationContextService correlationService,
                               EventBus eventBus) {
        super(correlationService, eventBus);
        this.streetRepository = streetRepository;
    }

    @Override
    protected Mono<StreetResult> process(Mono<UpdateStreetCommand> request) {
        return request.flatMap(cmd ->
                streetRepository.findById(StreetId.of(cmd.id()))
                        .switchIfEmpty(Mono.error(new PlaceNotFoundException("Street", cmd.id())))
                        .flatMap(existing -> {
                            var updated = existing.updateInfo(cmd.name(), cmd.nameEn(), cmd.nameTm(), cmd.cityId());
                            return streetRepository.save(updated);
                        })
                        .map(StreetResult::fromDomain)
        );
    }

    @Override
    protected String getBoundContext() {
        return "place";
    }
}
