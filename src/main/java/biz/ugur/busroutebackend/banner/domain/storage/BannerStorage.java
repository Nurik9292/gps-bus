package biz.ugur.busroutebackend.banner.domain.storage;

import reactor.core.publisher.Mono;


public interface BannerStorage {
    Mono<String> save(String base64Data);
    Mono<Void> delete(String path);
}
