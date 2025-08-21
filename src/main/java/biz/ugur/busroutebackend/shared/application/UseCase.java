package biz.ugur.busroutebackend.shared.application;

import reactor.core.publisher.Mono;

public interface UseCase<REQUEST, RESPONSE> {

    RESPONSE execute(REQUEST request);
}