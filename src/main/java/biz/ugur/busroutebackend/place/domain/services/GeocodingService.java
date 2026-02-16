package biz.ugur.busroutebackend.place.domain.services;

import biz.ugur.busroutebackend.place.application.dto.GeocodingResult;
import reactor.core.publisher.Mono;

import java.util.List;

public interface GeocodingService {

    Mono<List<GeocodingResult>> search(String query, int limit);

    Mono<GeocodingResult> reverse(double lat, double lon);
}
