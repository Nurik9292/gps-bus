package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.AliasResult;
import biz.ugur.busroutebackend.place.application.dto.CreatePlaceCommand;
import biz.ugur.busroutebackend.place.application.dto.PlaceResult;
import biz.ugur.busroutebackend.place.domain.model.Place;
import biz.ugur.busroutebackend.place.domain.model.PlaceAlias;
import biz.ugur.busroutebackend.place.domain.model.PlaceCategory;
import biz.ugur.busroutebackend.place.domain.repository.PlaceAliasRepository;
import biz.ugur.busroutebackend.place.domain.repository.PlaceRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.place.domain.events.PlaceCatalogChangedEvent;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
public class CreatePlaceUseCase extends BaseUseCase<Mono<CreatePlaceCommand>, PlaceResult> {

    private final PlaceRepository placeRepository;
    private final PlaceAliasRepository placeAliasRepository;

    public CreatePlaceUseCase(PlaceRepository placeRepository,
                              PlaceAliasRepository placeAliasRepository,
                              CorrelationContextService correlationService,
                              EventBus eventBus) {
        super(correlationService, eventBus);
        this.placeRepository = placeRepository;
        this.placeAliasRepository = placeAliasRepository;
    }

    @Override
    protected Mono<PlaceResult> process(Mono<CreatePlaceCommand> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "place";
    }

    private Mono<PlaceResult> processInternal(CreatePlaceCommand cmd) {
        Place place = Place.create(
                cmd.name(), cmd.nameEn(), cmd.nameTm(),
                cmd.description(), cmd.address(),
                PlaceCategory.valueOf(cmd.category()),
                cmd.cityId(), cmd.latitude(), cmd.longitude(),
                cmd.isActive()
        );

        return placeRepository.save(place)
                .flatMap(saved -> {
                    if (cmd.aliases() == null || cmd.aliases().isEmpty()) {
                        return Mono.just(PlaceResult.fromDomain(saved));
                    }

                    return Flux.fromIterable(cmd.aliases())
                            .flatMap(aliasCmd -> {
                                PlaceAlias alias = PlaceAlias.create(
                                        saved.getId().getValue(),
                                        aliasCmd.alias(),
                                        aliasCmd.language()
                                );
                                return placeAliasRepository.save(alias);
                            })
                            .map(AliasResult::fromDomain)
                            .collectList()
                            .map(aliases -> PlaceResult.fromDomain(saved, aliases));
                })
                .doOnSuccess(r -> log.info("Place created: {}", r.name()))
                .doOnSuccess(r -> eventBus.publish(new PlaceCatalogChangedEvent(r.id())));
    }
}
