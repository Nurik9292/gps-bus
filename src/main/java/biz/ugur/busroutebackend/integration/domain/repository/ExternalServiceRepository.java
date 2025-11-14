package biz.ugur.busroutebackend.integration.domain.repository;

import biz.ugur.busroutebackend.integration.domain.model.ExternalService;
import biz.ugur.busroutebackend.integration.domain.valueobjects.ApiToken;
import biz.ugur.busroutebackend.integration.domain.valueobjects.ExternalServiceId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ExternalServiceRepository {

    Mono<ExternalService> save(ExternalService externalService);

    Mono<ExternalService> findById(ExternalServiceId id);

    Mono<ExternalService> findByApiToken(ApiToken apiToken);

    Flux<ExternalService> findAll();

    Flux<ExternalService> findAllActive();

    Mono<Void> deleteById(ExternalServiceId id);

    Mono<Boolean> existsByName(String name);

    Mono<Long> count();

    Mono<Long> countActive();
}
