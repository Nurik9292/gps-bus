package biz.ugur.busroutebackend.admin.domain.storage;

import biz.ugur.busroutebackend.shared.infrastructure.storage.BaseImageStorageService;
import reactor.core.publisher.Mono;

/**
 * Domain interface for avatar storage operations.
 * Defines the contract for saving and deleting admin user avatars.
 */
public interface AvatarStorage {

    /**
     * Save admin avatar image from base64 data.
     *
     * @param adminId the admin user identifier
     * @param base64Data base64 encoded image data with mime type prefix
     * @return mono containing storage result with paths and sizes
     */
    Mono<BaseImageStorageService.Result> save(String adminId, String base64Data);

    /**
     * Delete avatar image and its thumbnail.
     *
     * @param path the path to the avatar file
     * @return mono that completes when deletion is done
     */
    Mono<Void> delete(String path);
}
