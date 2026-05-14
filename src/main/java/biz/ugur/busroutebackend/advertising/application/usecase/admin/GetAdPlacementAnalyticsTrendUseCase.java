package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementAnalyticsTrendResponse;
import biz.ugur.busroutebackend.advertising.application.dto.DailyAnalyticsPoint;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdPlacementNotFoundException;
import biz.ugur.busroutebackend.advertising.domain.repository.AdClickEventRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdDetailViewEventRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdImpressionEventRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.DetailViewSummary;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class GetAdPlacementAnalyticsTrendUseCase
        extends BaseUseCase<GetAdPlacementAnalyticsTrendUseCase.Query, AdPlacementAnalyticsTrendResponse> {

    private static final Duration MAX_PERIOD = Duration.ofDays(365);

    public record Query(PlacementId placementId, Instant from, Instant to) {}

    private final AdPlacementRepository placementRepository;
    private final AdImpressionEventRepository impressionRepo;
    private final AdClickEventRepository clickRepo;
    private final AdDetailViewEventRepository detailViewRepo;
    private final Clock clock;

    public GetAdPlacementAnalyticsTrendUseCase(AdPlacementRepository placementRepository,
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
    protected Mono<AdPlacementAnalyticsTrendResponse> process(Query q) {
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
                            impressionRepo.countByDayBetween(q.placementId(), from, to),
                            clickRepo.countByDayBetween(q.placementId(), from, to),
                            detailViewRepo.summarizeByDayBetween(q.placementId(), from, to)
                    ).map(t -> AdPlacementAnalyticsTrendResponse.of(
                            q.placementId(), from, to,
                            mergeDays(from, to, t.getT1(), t.getT2(), t.getT3())));
                });
    }

    private List<DailyAnalyticsPoint> mergeDays(
            Instant from, Instant to,
            Map<LocalDate, Long> impressions,
            Map<LocalDate, Long> clicks,
            Map<LocalDate, DetailViewSummary> detailViews) {
        LocalDate start = from.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate end   = to.atZone(ZoneOffset.UTC).toLocalDate();
        List<DailyAnalyticsPoint> points = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            long imp = impressions.getOrDefault(day, 0L);
            long clk = clicks.getOrDefault(day, 0L);
            DetailViewSummary dv = detailViews.getOrDefault(day, new DetailViewSummary(0L, null));
            BigDecimal ctr = imp == 0 ? null
                    : BigDecimal.valueOf(clk).multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(imp), 2, RoundingMode.HALF_UP);
            Long avgDwell = dv.count() == 0 ? null : dv.avgDurationMs();
            points.add(new DailyAnalyticsPoint(day, imp, clk, ctr, dv.count(), avgDwell));
        }
        return points;
    }

    @Override
    protected String getBoundContext() {
        return "advertising.admin";
    }
}
