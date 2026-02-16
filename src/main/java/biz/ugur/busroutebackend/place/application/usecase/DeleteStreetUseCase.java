package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.domain.exceptions.PlaceNotFoundException;
import biz.ugur.busroutebackend.place.domain.repository.StreetRepository;
import biz.ugur.busroutebackend.place.domain.valueobjects.StreetId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class DeleteStreetUseCase extends BaseUseCase<Mono<String>, Void> {

    private final StreetRepository streetRepository;

    public DeleteStreetUseCase(StreetRepository streetRepository,
                               CorrelationContextService correlationService,
                               EventBus eventBus) {
        super(correlationService, eventBus);
        this.streetRepository = streetRepository;
    }

    @Override
    protected Mono<Void> process(Mono<String> request) {
        return request.flatMap(id ->
                streetRepository.findById(StreetId.of(id))
                        .switchIfEmpty(Mono.error(new PlaceNotFoundException("Street", id)))
                        .flatMap(street -> streetRepository.deleteById(street.getId()))
        );
    }

    @Override
    protected String getBoundContext() {
        return "place";
    }
}
