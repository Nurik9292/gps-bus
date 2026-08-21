package biz.ugur.busroutebackend.advertising.infrastructure.external.tanat;

import biz.ugur.busroutebackend.advertising.application.dto.integration.ExternalBannerCommand;
import biz.ugur.busroutebackend.advertising.application.usecase.integration.UpsertExternalBannerUseCase;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@ConditionalOnProperty(prefix = "external.api.tanat", name = "enabled", havingValue = "true")
@Slf4j
public class TanatBannerSyncService {

    private static final Duration PER_ITEM_TIMEOUT = Duration.ofSeconds(10);
    private static final int CONCURRENCY = 3;
    private static final String ROUTES_TYPE = "routes";

    private final TanatBannerApiClient apiClient;
    private final TanatBannerProperties properties;
    private final UpsertExternalBannerUseCase upsertUseCase;
    private final AdPlacementRepository placementRepository;

    public TanatBannerSyncService(TanatBannerApiClient apiClient,
                                  TanatBannerProperties properties,
                                  UpsertExternalBannerUseCase upsertUseCase,
                                  AdPlacementRepository placementRepository) {
        this.apiClient = apiClient;
        this.properties = properties;
        this.upsertUseCase = upsertUseCase;
        this.placementRepository = placementRepository;
    }

    public Mono<Void> synchronize() {
        return fetchOffer()
                .flatMap(offer -> {
                    if (!offer.answered()) {
                        log.warn("[TANAT] no combination answered — keeping stored banners as they are");
                        return Mono.empty();
                    }
                    return placementRepository.findByExternalServiceId(properties.getServiceId())
                            .collectList()
                            .flatMap(stored -> reconcile(offer.banners(), stored));
                });
    }

    private Mono<Void> reconcile(List<TanatBannerResponse.Banner> offered, List<AdPlacement> stored) {
        Set<String> takenDownByAdmin = stored.stream()
                .filter(placement -> placement.getStatus() == PlacementStatus.CANCELLED)
                .map(AdPlacement::getExternalRef)
                .collect(java.util.stream.Collectors.toSet());

        List<TanatBannerResponse.Banner> toStore = offered.stream()
                .filter(banner -> !takenDownByAdmin.contains(banner.hash()))
                .toList();

        Set<String> stillOffered = hashesOf(offered);
        List<AdPlacement> toWithdraw = stored.stream()
                .filter(placement -> placement.getStatus() == PlacementStatus.ACTIVE
                        || placement.getStatus() == PlacementStatus.SCHEDULED)
                .filter(placement -> !stillOffered.contains(placement.getExternalRef()))
                .toList();

        return storeOffered(toStore).then(withdraw(toWithdraw));
    }

    private Mono<Offer> fetchOffer() {
        return Flux.fromIterable(combinations())
                .flatMap(this::fetchQuietly, CONCURRENCY)
                .collectList()
                .map(Offer::of);
    }

    private Mono<TanatBannerResponse> fetchQuietly(Combination combination) {
        return apiClient.fetchBanner(combination.language(), combination.device(), combination.operatingSystem())
                .timeout(PER_ITEM_TIMEOUT)
                .onErrorResume(error -> Mono.empty());
    }

    private Mono<Void> storeOffered(List<TanatBannerResponse.Banner> banners) {
        return Flux.fromIterable(banners)
                .index()
                .concatMap(indexed -> store(indexed.getT2(), indexed.getT1().intValue()))
                .then();
    }

    private Mono<Void> store(TanatBannerResponse.Banner banner, int displayOrder) {
        return apiClient.downloadImage(banner.bannerFile())
                .timeout(PER_ITEM_TIMEOUT)
                .map(bytes -> asDataUrl(bytes, banner.bannerFile()))
                .flatMap(image -> upsertUseCase.execute(Mono.just(new ExternalBannerCommand(
                        properties.getServiceId(),
                        banner.hash(),
                        ROUTES_TYPE,
                        titleFor(banner),
                        image,
                        banner.url(),
                        null,
                        null,
                        null,
                        displayOrder))))
                .doOnNext(saved -> log.info("[TANAT] banner stored ref={} status={}",
                        saved.getExternalRef(), saved.getStatus()))
                .onErrorResume(error -> {
                    log.warn("[TANAT] cannot store banner {}: {}", banner.hash(), error.toString());
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Void> withdraw(List<AdPlacement> placements) {
        return Flux.fromIterable(placements)
                .concatMap(this::pauseQuietly)
                .then();
    }

    private Mono<AdPlacement> pauseQuietly(AdPlacement placement) {
        return Mono.fromSupplier(placement::markAsPaused)
                .flatMap(placementRepository::save)
                .doOnNext(paused -> log.info("[TANAT] banner withdrawn, no longer offered ref={}",
                        paused.getExternalRef()))
                .onErrorResume(error -> {
                    log.warn("[TANAT] cannot withdraw {}: {}", placement.getExternalRef(), error.toString());
                    return Mono.empty();
                });
    }

    private static Set<String> hashesOf(List<TanatBannerResponse.Banner> banners) {
        return banners.stream().map(TanatBannerResponse.Banner::hash)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static List<TanatBannerResponse.Banner> firstOccurrencePerHash(List<TanatBannerResponse.Banner> banners) {
        Set<String> seen = new LinkedHashSet<>();
        return banners.stream().filter(banner -> seen.add(banner.hash())).toList();
    }

    private static String titleFor(TanatBannerResponse.Banner banner) {
        return "tanat " + banner.hash();
    }

    private static String asDataUrl(byte[] bytes, String sourceUrl) {
        String mimeType = sourceUrl.toLowerCase().endsWith(".png") ? "image/png"
                : sourceUrl.toLowerCase().endsWith(".webp") ? "image/webp"
                : "image/jpeg";
        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private List<Combination> combinations() {
        return properties.getLanguages().stream()
                .flatMap(language -> properties.getDevices().stream()
                        .flatMap(device -> properties.getOperatingSystems().stream()
                                .map(os -> new Combination(language, device, os))))
                .toList();
    }

    private record Combination(String language, String device, String operatingSystem) {
    }

    private record Offer(boolean answered, List<TanatBannerResponse.Banner> banners) {

        static Offer of(List<TanatBannerResponse> responses) {
            if (responses.isEmpty()) {
                return new Offer(false, List.of());
            }
            List<TanatBannerResponse.Banner> banners = responses.stream()
                    .filter(TanatBannerResponse::carriesBanner)
                    .map(TanatBannerResponse::bannerOrNull)
                    .toList();
            return new Offer(true, firstOccurrencePerHash(banners));
        }
    }
}
