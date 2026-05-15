package biz.ugur.busroutebackend.advertising.application.processor;

import biz.ugur.busroutebackend.advertising.domain.storage.AdPlacementStorage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class AdPlacementImageProcessor {

    private static final String DATA_URL_PREFIX = "data:image/";

    private final AdPlacementStorage storage;

    public AdPlacementImageProcessor(AdPlacementStorage storage) {
        this.storage = storage;
    }

    public Mono<String> process(String imageUrl) {
        if (imageUrl != null && imageUrl.startsWith(DATA_URL_PREFIX)) {
            return storage.save(imageUrl);
        }
        return Mono.justOrEmpty(imageUrl);
    }

    public Mono<String> processForUpdate(String newImageUrl, String oldImageUrl) {
        if (newImageUrl != null && newImageUrl.startsWith(DATA_URL_PREFIX)) {
            return storage.save(newImageUrl)
                    .flatMap(savedPath -> deleteIfStoredPath(oldImageUrl).thenReturn(savedPath));
        }
        if (newImageUrl == null || newImageUrl.equals(oldImageUrl)) {
            return Mono.justOrEmpty(oldImageUrl);
        }
        return deleteIfStoredPath(oldImageUrl).thenReturn(newImageUrl);
    }

    private Mono<Void> deleteIfStoredPath(String path) {
        if (path == null || path.isBlank() || path.startsWith(DATA_URL_PREFIX)) {
            return Mono.empty();
        }
        return storage.delete(path);
    }
}
