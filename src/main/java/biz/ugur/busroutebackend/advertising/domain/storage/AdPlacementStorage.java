package biz.ugur.busroutebackend.advertising.domain.storage;

import reactor.core.publisher.Mono;

public interface AdPlacementStorage {

    Mono<String> save(String base64Data);

    Mono<Void> delete(String path);
}
