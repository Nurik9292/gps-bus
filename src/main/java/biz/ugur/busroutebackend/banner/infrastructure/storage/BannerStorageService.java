package biz.ugur.busroutebackend.banner.infrastructure.storage;

import biz.ugur.busroutebackend.banner.domain.storage.BannerStorage;
import biz.ugur.busroutebackend.shared.infrastructure.storage.BaseImageStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class BannerStorageService extends BaseImageStorageService implements BannerStorage {

    @Value("${app.storage.banners.base-path:/app/data/banners}")
    private String basePath;

    @Value("${app.storage.banners.max-size-mb:10}")
    private int maxSizeMb;

    @Value("${app.storage.banners.create-thumbnails:false}")
    private boolean createThumbnails;

    @Value("${app.storage.banners.thumbnail-width:400}")
    private int thumbnailWidth;

    @Value("${app.storage.banners.thumbnail-height:200}")
    private int thumbnailHeight;

    @Value("${app.storage.banners.jpeg-quality:0.85}")
    private float jpegQuality;


    @Override
    public Mono<String> save(String base64Data) {
        log.debug("Saving banner image, data length: {} bytes", base64Data != null ? base64Data.length() : 0);
        return storeBase64Image(base64Data)
                .map(Result::getDisplayUrl)
                .doOnSuccess(url -> log.info("Banner image saved successfully: {}", url))
                .doOnError(error -> log.error("Failed to save banner image", error));
    }

    @Override
    public Mono<Void> delete(String path) {
        log.debug("Deleting banner image: {}", path);
        return deleteFile(path)
                .doOnSuccess(v -> log.info("Banner image deleted successfully: {}", path))
                .doOnError(error -> log.error("Failed to delete banner image: {}", path, error));
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
        return thumbnailWidth;
    }

    @Override
    protected int thumbnailHeight() {
        return thumbnailHeight;
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
        return "/banners/"; 
    }
}