package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.application.dto.SalesReportResponse;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.SalesReportFilter;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentStatus;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Service
public class GetSalesReportUseCase
        extends BaseUseCase<GetSalesReportUseCase.Query, SalesReportResponse> {

    private static final Duration MAX_PERIOD = Duration.ofDays(365);
    private static final int MAX_SIZE = 100;

    public record Query(
            Instant from, Instant to,
            int page, int size,
            PaymentStatus paymentStatus, PaymentProvider provider) {}

    private final AdPlacementRepository placementRepository;

    public GetSalesReportUseCase(AdPlacementRepository placementRepository,
                                 CorrelationContextService correlationService,
                                 EventBus eventBus) {
        super(correlationService, eventBus);
        this.placementRepository = placementRepository;
    }

    @Override
    protected Mono<SalesReportResponse> process(Query q) {
        if (q.page() < 1) return Mono.error(new IllegalArgumentException("page must be >= 1"));
        if (q.size() < 1 || q.size() > MAX_SIZE)
            return Mono.error(new IllegalArgumentException("size must be in [1, " + MAX_SIZE + "]"));
        if (!q.from().isBefore(q.to()))
            return Mono.error(new IllegalArgumentException("from must be < to"));
        if (Duration.between(q.from(), q.to()).compareTo(MAX_PERIOD) > 0)
            return Mono.error(new IllegalArgumentException("period exceeds 365 days"));

        SalesReportFilter filter = new SalesReportFilter(
                q.from(), q.to(), q.page(), q.size(),
                q.paymentStatus(), q.provider());

        return Mono.zip(
                placementRepository.findForSalesReport(filter).collectList(),
                placementRepository.countForSalesReport(filter),
                placementRepository.totalsForSalesReport(filter)
        ).map(t -> SalesReportResponse.of(t.getT1(), t.getT2(), t.getT3(), q.page(), q.size()));
    }

    @Override
    protected String getBoundContext() {
        return "advertising.admin";
    }
}
