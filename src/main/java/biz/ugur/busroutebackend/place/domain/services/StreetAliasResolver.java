package biz.ugur.busroutebackend.place.domain.services;

import reactor.core.publisher.Mono;

public interface StreetAliasResolver {

    Mono<String> resolveStreetAliases(String query, String cityId);
}
