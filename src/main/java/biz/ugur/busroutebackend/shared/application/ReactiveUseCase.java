package biz.ugur.busroutebackend.shared.application;

import reactor.core.publisher.Mono;

public interface ReactiveUseCase<REQUEST, RESPONSE> {

    Mono<RESPONSE> execute(REQUEST request);
}
