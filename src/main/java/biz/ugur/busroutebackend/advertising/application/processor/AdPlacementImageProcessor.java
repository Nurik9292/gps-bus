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
        if (newImageUrl == null || newImageUrl.equals(oldImageUrl)) {
            return Mono.justOrEmpty(oldImageUrl);
        }
        if (newImageUrl.startsWith(DATA_URL_PREFIX)) {
            return storage.save(newImageUrl)
                    .flatMap(savedPath -> storage.delete(oldImageUrl).thenReturn(savedPath));
        }
        return storage.delete(oldImageUrl).thenReturn(newImageUrl);
    }
}
