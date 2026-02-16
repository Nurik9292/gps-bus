package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.PlaceResult;
import biz.ugur.busroutebackend.place.application.dto.UpdatePlaceCommand;
import biz.ugur.busroutebackend.place.domain.exceptions.PlaceNotFoundException;
import biz.ugur.busroutebackend.place.domain.model.PlaceCategory;
import biz.ugur.busroutebackend.place.domain.repository.PlaceRepository;
import biz.ugur.busroutebackend.place.domain.valueobjects.PlaceId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class UpdatePlaceUseCase extends BaseUseCase<Mono<UpdatePlaceCommand>, PlaceResult> {

    private final PlaceRepository placeRepository;

    public UpdatePlaceUseCase(PlaceRepository placeRepository,
                              CorrelationContextService correlationService,
                              EventBus eventBus) {
        super(correlationService, eventBus);
        this.placeRepository = placeRepository;
    }

    @Override
    protected Mono<PlaceResult> process(Mono<UpdatePlaceCommand> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "place";
    }

    private Mono<PlaceResult> processInternal(UpdatePlaceCommand cmd) {
        return placeRepository.findById(PlaceId.of(cmd.id()))
                .switchIfEmpty(Mono.error(new PlaceNotFoundException(cmd.id())))
                .flatMap(existing -> {
                    PlaceCategory category = cmd.category() != null ? PlaceCategory.valueOf(cmd.category()) : null;
                    var updated = existing.updateInfo(
                            cmd.name(), cmd.nameEn(), cmd.nameTm(),
                            cmd.description(), cmd.address(),
                            category, cmd.cityId(),
                            cmd.latitude(), cmd.longitude()
                    );
                    return placeRepository.save(updated);
                })
                .map(PlaceResult::fromDomain)
                .doOnSuccess(r -> log.info("Place updated: {}", r.id()));
    }
}
