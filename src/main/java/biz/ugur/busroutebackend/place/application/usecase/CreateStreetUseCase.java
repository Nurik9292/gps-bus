package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.CreateStreetCommand;
import biz.ugur.busroutebackend.place.application.dto.StreetResult;
import biz.ugur.busroutebackend.place.domain.model.Street;
import biz.ugur.busroutebackend.place.domain.repository.StreetRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CreateStreetUseCase extends BaseUseCase<Mono<CreateStreetCommand>, StreetResult> {

    private final StreetRepository streetRepository;

    public CreateStreetUseCase(StreetRepository streetRepository,
                               CorrelationContextService correlationService,
                               EventBus eventBus) {
        super(correlationService, eventBus);
        this.streetRepository = streetRepository;
    }

    @Override
    protected Mono<StreetResult> process(Mono<CreateStreetCommand> request) {
        return request.flatMap(cmd -> {
            Street street = Street.create(cmd.name(), cmd.nameEn(), cmd.nameTm(), cmd.cityId());
            return streetRepository.save(street).map(StreetResult::fromDomain);
        });
    }

    @Override
    protected String getBoundContext() {
        return "place";
    }
}
