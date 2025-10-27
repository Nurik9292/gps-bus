package biz.ugur.busroutebackend.banner.application.processor;

import biz.ugur.busroutebackend.banner.domain.storage.BannerStorage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Component
public class BannerImageProcessor {

    private final BannerStorage storage;

    public BannerImageProcessor(BannerStorage storage) {
        this.storage = storage;
    }

    public Mono<String> process(String imageUrl) {
        if (imageUrl != null && imageUrl.startsWith("data:image/")) {
            return storage.save(imageUrl);
        }
        return Mono.justOrEmpty(imageUrl);
    }


    public Mono<String> processForUpdate(String newImageUrl, String oldImageUrl) {
        if (newImageUrl == null || newImageUrl.equals(oldImageUrl)) {
            return Mono.just(oldImageUrl);
        }

        if (newImageUrl.startsWith("data:image/")) {
            return storage.save(newImageUrl)
                    .flatMap(savedPath -> storage.delete(oldImageUrl)
                            .thenReturn(savedPath));
        }

        return storage.delete(oldImageUrl)
                .thenReturn(newImageUrl);
    }
}
