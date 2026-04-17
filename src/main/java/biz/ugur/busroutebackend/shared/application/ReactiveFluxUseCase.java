package biz.ugur.busroutebackend.shared.application;

import reactor.core.publisher.Flux;

public interface ReactiveFluxUseCase<REQUEST, RESPONSE> {

    Flux<RESPONSE> execute(REQUEST request);
}
