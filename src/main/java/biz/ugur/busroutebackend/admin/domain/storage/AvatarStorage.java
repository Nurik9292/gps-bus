package biz.ugur.busroutebackend.admin.domain.storage;

import biz.ugur.busroutebackend.shared.infrastructure.storage.BaseImageStorageService;
import reactor.core.publisher.Mono;

public interface AvatarStorage {

    Mono<BaseImageStorageService.Result> save(String adminId, String base64Data);

    Mono<Void> delete(String path);
}
