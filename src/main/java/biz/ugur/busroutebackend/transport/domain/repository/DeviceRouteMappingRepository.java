package biz.ugur.busroutebackend.transport.domain.repository;

import reactor.core.publisher.Mono;

import java.util.Map;


public interface DeviceRouteMappingRepository {


    Mono<Map<String, String>> getMapping();

    Mono<Void> updateMapping(Map<String, String> mapping);
}
