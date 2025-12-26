package biz.ugur.busroutebackend.admin.infrastructure.storage;

import biz.ugur.busroutebackend.admin.domain.storage.AvatarStorage;
import biz.ugur.busroutebackend.shared.infrastructure.storage.BaseImageStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;


@Slf4j
@Service
public class AvatarStorageService extends BaseImageStorageService implements AvatarStorage {

    @Value("${app.storage.avatars.base-path:/app/data/avatars}")
    private String basePath;

    @Value("${app.storage.avatars.max-size-mb:2}")
    private int maxSizeMb;

    @Value("${app.storage.avatars.create-thumbnails:true}")
    private boolean createThumbnails;

    @Value("${app.storage.avatars.thumbnail-size:150}")
    private int thumbnailSize;

    @Value("${app.storage.avatars.jpeg-quality:0.85}")
    private float jpegQuality;


    @Override
    public Mono<Result> save(String adminId, String base64Data) {
        log.info("💾 Saving avatar for admin: {}", adminId);

        return storeBase64Image(base64Data)
                .doOnSuccess(result -> log.info(
                    "✅ Avatar saved successfully for admin {}: original={}, thumbnail={}, size={}KB",
                    adminId,
                    result.originalPath(),
                    result.thumbnailPath(),
                    result.originalSize() / 1024
                ))
                .doOnError(error -> log.error(
                    "❌ Failed to save avatar for admin {}: {}",
                    adminId,
                    error.getMessage()
                ));
    }


    @Override
    public Mono<Void> delete(String avatarPath) {
        if (avatarPath == null || avatarPath.startsWith("data:")) {
            return Mono.empty();
        }

        log.debug("🗑️ Deleting avatar: {}", avatarPath);

        return deleteFile(avatarPath)
                .doOnSuccess(v -> log.info("✅ Avatar deleted successfully: {}", avatarPath))
                .doOnError(error -> log.warn("⚠️ Failed to delete avatar {}: {}", avatarPath, error.getMessage()));
    }


    @Override
    protected String basePath() {
        return basePath;
    }

    @Override
    protected int maxSizeMb() {
        return maxSizeMb;
    }

    @Override
    protected int thumbnailWidth() {
        return thumbnailSize;
    }

    @Override
    protected int thumbnailHeight() {
        return thumbnailSize;
    }

    @Override
    protected boolean createThumbnails() {
        return createThumbnails;
    }

    @Override
    protected float jpegQuality() {
        return jpegQuality;
    }

    @Override
    protected String dir() {
        return "/avatars/";
    }
}
