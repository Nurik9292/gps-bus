package biz.ugur.busroutebackend.advertising.infrastructure.external.tanat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnProperty(prefix = "external.api.tanat", name = "enabled", havingValue = "true")
@Slf4j
public class TanatBannerApiClient {

    private static final String BANNER_PATH = "/api/client-data/mobile";

    private final WebClient webClient;
    private final TanatBannerProperties properties;

    public TanatBannerApiClient(@Qualifier("tanatBannerClient") WebClient webClient,
                                TanatBannerProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    public Mono<TanatBannerResponse> fetchBanner(String language, String device, String operatingSystem) {
        return webClient.get()
                .uri(builder -> builder.path(BANNER_PATH)
                        .queryParam("position_key", properties.getPositionKey())
                        .queryParam("lang", language)
                        .queryParam("device", device)
                        .queryParam("os", operatingSystem)
                        .build())
                .retrieve()
                .bodyToMono(TanatBannerResponse.class)
                .doOnNext(response -> logOutcome(response, language, device, operatingSystem))
                .doOnError(error -> log.warn("[TANAT] fetch failed lang={} device={} os={}: {}",
                        language, device, operatingSystem, error.toString()));
    }

    public Mono<byte[]> downloadImage(String imageUrl) {
        return webClient.get()
                .uri(imageUrl)
                .retrieve()
                .bodyToMono(byte[].class)
                .doOnError(error -> log.warn("[TANAT] image download failed url={}: {}", imageUrl, error.toString()));
    }

    private static void logOutcome(TanatBannerResponse response, String language, String device, String os) {
        if (response.carriesBanner()) {
            log.debug("[TANAT] banner {} for lang={} device={} os={}",
                    response.bannerOrNull().hash(), language, device, os);
        } else {
            log.debug("[TANAT] no banner for lang={} device={} os={}", language, device, os);
        }
    }
}
