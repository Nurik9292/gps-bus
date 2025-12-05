package biz.ugur.busroutebackend.transport.application.usecase.routealternative;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAlternativeRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteAlternativeId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class DeleteRouteAlternativeUseCase extends BaseUseCase<DeleteRouteAlternativeUseCase.Request, Void> {

    private final RouteAlternativeRepository routeAlternativeRepository;

    public DeleteRouteAlternativeUseCase(
            RouteAlternativeRepository routeAlternativeRepository,
            CorrelationContextService correlationService,
            EventBus eventBus) {
        super(correlationService, eventBus);
        this.routeAlternativeRepository = routeAlternativeRepository;
    }

    @Override
    protected Mono<Void> process(Request request) {
        RouteAlternativeId id = RouteAlternativeId.of(request.id);

        return routeAlternativeRepository.existsById(id)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new IllegalArgumentException(
                                "Route alternative not found: " + request.id));
                    }
                    return routeAlternativeRepository.deleteById(id);
                })
                .doOnSuccess(v -> log.info("Deleted route alternative: {}", request.id));
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    public record Request(String id) {}
}
