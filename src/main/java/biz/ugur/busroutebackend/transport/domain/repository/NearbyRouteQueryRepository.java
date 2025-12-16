package biz.ugur.busroutebackend.transport.domain.repository;

import biz.ugur.busroutebackend.transport.domain.valueobject.NearbyRouteInfo;
import reactor.core.publisher.Flux;

public interface NearbyRouteQueryRepository {

    Flux<NearbyRouteInfo> findRoutesNearLocation(Double latitude, Double longitude, Integer radiusMeters);
}
