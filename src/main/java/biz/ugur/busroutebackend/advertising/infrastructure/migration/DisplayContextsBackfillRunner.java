package biz.ugur.busroutebackend.advertising.infrastructure.migration;

import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementTargetRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementTarget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class DisplayContextsBackfillRunner implements ApplicationRunner {

    private static final Map<String, TargetType> CONTEXT_TO_TYPE = buildMapping();

    private final AdPlacementRepository placementRepository;
    private final AdPlacementTargetRepository targetRepository;

    public DisplayContextsBackfillRunner(AdPlacementRepository placementRepository,
                                          AdPlacementTargetRepository targetRepository) {
        this.placementRepository = placementRepository;
        this.targetRepository = targetRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        placementRepository.findAll()
                .filter(this::hasParseableContexts)
                .concatMap(this::backfillIfMissingTargets)
                .doOnError(err -> log.error(
                        "DisplayContextsBackfillRunner failed", err))
                .onErrorResume(err -> Mono.empty())
                .subscribe();
    }

    private boolean hasParseableContexts(AdPlacement placement) {
        String contexts = placement.getDisplayContexts();
        return contexts != null && !contexts.isBlank();
    }

    private Mono<Void> backfillIfMissingTargets(AdPlacement placement) {
        PlacementId placementId = placement.getId();
        return targetRepository.findByPlacementId(placementId)
                .hasElements()
                .flatMap(hasTargets -> {
                    if (Boolean.TRUE.equals(hasTargets)) {
                        return Mono.empty();
                    }
                    List<PlacementTarget> parsed = parseTargets(
                            placementId.getValue(), placement.getDisplayContexts());
                    if (parsed.isEmpty()) {
                        return Mono.empty();
                    }
                    log.info("Backfilling {} target(s) for placement {} from display_contexts='{}'",
                            parsed.size(), placementId.getValue(), placement.getDisplayContexts());
                    return targetRepository.replaceAll(placementId, parsed);
                });
    }

    static List<PlacementTarget> parseTargets(String placementId, String displayContexts) {
        if (displayContexts == null || displayContexts.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(displayContexts.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .map(token -> {
                    TargetType type = CONTEXT_TO_TYPE.get(token);
                    if (type == null) {
                        log.warn("Skipping unknown display_context '{}' for placement {}",
                                token, placementId);
                        return null;
                    }
                    return PlacementTarget.general(type);
                })
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private static Map<String, TargetType> buildMapping() {
        Map<String, TargetType> map = new HashMap<>();
        map.put("home", TargetType.HOME);
        map.put("popup", TargetType.POPUP);
        map.put("routes", TargetType.ROUTES_LIST);
        map.put("stops", TargetType.STOPS_LIST);
        map.put("places", TargetType.PLACES_LIST);
        return Map.copyOf(map);
    }
}
