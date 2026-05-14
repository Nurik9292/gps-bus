package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementAnalyticsResponse;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdPlacementNotFoundException;
import biz.ugur.busroutebackend.advertising.domain.repository.AdClickEventRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdDetailViewEventRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdImpressionEventRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

@Service
public class GetAdPlacementAnalyticsUseCase
        extends BaseUseCase<GetAdPlacementAnalyticsUseCase.Query, AdPlacementAnalyticsResponse> {

    private static final Duration MAX_PERIOD = Duration.ofDays(365);

    public record Query(PlacementId placementId, Instant from, Instant to) {}

    private final AdPlacementRepository placementRepository;
    private final AdImpressionEventRepository impressionRepo;
    private final AdClickEventRepository clickRepo;
    private final AdDetailViewEventRepository detailViewRepo;
    private final Clock clock;

    public GetAdPlacementAnalyticsUseCase(AdPlacementRepository placementRepository,
                                          AdImpressionEventRepository impressionRepo,
                                          AdClickEventRepository clickRepo,
                                          AdDetailViewEventRepository detailViewRepo,
                                          Clock clock,
                                          CorrelationContextService correlationService,
                                          EventBus eventBus) {
        super(correlationService, eventBus);
        this.placementRepository = placementRepository;
        this.impressionRepo = impressionRepo;
        this.clickRepo = clickRepo;
        this.detailViewRepo = detailViewRepo;
        this.clock = clock;
    }

    @Override
    protected Mono<AdPlacementAnalyticsResponse> process(Query q) {
        return placementRepository.findById(q.placementId())
                .switchIfEmpty(Mono.error(new AdPlacementNotFoundException(q.placementId().getValue())))
                .flatMap(placement -> {
                    Instant from = q.from() != null ? q.from()
                            : placement.getCreatedAt().toInstant(ZoneOffset.UTC);
                    Instant to   = q.to()   != null ? q.to() : clock.instant();

                    if (!from.isBefore(to)) {
                        return Mono.error(new IllegalArgumentException("from must be < to"));
                    }
                    if (Duration.between(from, to).compareTo(MAX_PERIOD) > 0) {
                        return Mono.error(new IllegalArgumentException("period exceeds 365 days"));
                    }

                    return Mono.zip(
                            impressionRepo.countByPlacementIdAndOccurredAtBetween(q.placementId(), from, to),
                            clickRepo.countByPlacementIdAndOccurredAtBetween(q.placementId(), from, to),
                            detailViewRepo.summarizeByPlacementIdAndOccurredAtBetween(q.placementId(), from, to)
                    ).map(t -> AdPlacementAnalyticsResponse.of(
                            q.placementId(), from, to,
                            t.getT1(), t.getT2(), t.getT3()
                    ));
                });
    }

    @Override
    protected String getBoundContext() {
        return "advertising.admin";
    }
}
