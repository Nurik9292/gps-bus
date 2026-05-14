package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.application.dto.SalesReportItem;
import biz.ugur.busroutebackend.advertising.application.dto.SalesReportTotals;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSalesReportUseCaseTest {

    @Mock private AdPlacementRepository placementRepository;
    @Mock private CorrelationContextService correlationService;
    @Mock private EventBus eventBus;

    private GetSalesReportUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetSalesReportUseCase(placementRepository, correlationService, eventBus);
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void happyPath_returnsItemsTotalsAndPagination() {
        var query = new GetSalesReportUseCase.Query(
                Instant.parse("2026-04-14T00:00:00Z"),
                Instant.parse("2026-05-14T00:00:00Z"),
                1, 20, null, null);

        SalesReportItem item = mock(SalesReportItem.class);
        SalesReportTotals totals = new SalesReportTotals(23, 11500, "TMT", new BigDecimal("3.89"));
        when(placementRepository.findForSalesReport(any())).thenReturn(Flux.just(item));
        when(placementRepository.countForSalesReport(any())).thenReturn(Mono.just(23L));
        when(placementRepository.totalsForSalesReport(any())).thenReturn(Mono.just(totals));

        StepVerifier.create(useCase.execute(query))
                .assertNext(resp -> {
                    assertThat(resp.items()).hasSize(1);
                    assertThat(resp.totals().orders()).isEqualTo(23);
                    assertThat(resp.pagination().page()).isEqualTo(1);
                    assertThat(resp.pagination().totalItems()).isEqualTo(23);
                    assertThat(resp.pagination().totalPages()).isEqualTo(2);
                }).verifyComplete();
    }

    @Test
    void invalidPage_throws() {
        var q = new GetSalesReportUseCase.Query(
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-14T00:00:00Z"),
                0, 20, null, null);
        StepVerifier.create(useCase.execute(q))
                .expectErrorSatisfies(err -> assertThat(err).isInstanceOf(IllegalArgumentException.class))
                .verify();
    }

    @Test
    void sizeOverMax_throws() {
        var q = new GetSalesReportUseCase.Query(
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-14T00:00:00Z"),
                1, 200, null, null);
        StepVerifier.create(useCase.execute(q))
                .expectErrorSatisfies(err -> assertThat(err).isInstanceOf(IllegalArgumentException.class))
                .verify();
    }

    @Test
    void periodOver365_throws() {
        var q = new GetSalesReportUseCase.Query(
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"),
                1, 20, null, null);
        StepVerifier.create(useCase.execute(q))
                .expectErrorSatisfies(err -> assertThat(err).isInstanceOf(IllegalArgumentException.class))
                .verify();
    }
}
