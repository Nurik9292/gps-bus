package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetAdPlacementStatusCountsUseCaseTest {

    @InjectMocks
    private GetAdPlacementStatusCountsUseCase useCase;

    @Mock
    private AdPlacementRepository placementRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void returnsCountsSummedByStatus() {
        Map<PlacementStatus, Long> counts = Map.of(
                PlacementStatus.DRAFT, 2L,
                PlacementStatus.ACTIVE, 5L,
                PlacementStatus.EXPIRED, 3L);

        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(placementRepository.countsByStatus()).thenReturn(Mono.just(counts));

        StepVerifier.create(useCase.execute(null))
                .assertNext(result -> {
                    assertEquals(2L, result.draft());
                    assertEquals(5L, result.active());
                    assertEquals(3L, result.expired());
                    assertEquals(10L, result.total());
                })
                .verifyComplete();
    }

    @Test
    void returnsZerosForEmptyMap() {
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(placementRepository.countsByStatus()).thenReturn(Mono.just(Map.of()));

        StepVerifier.create(useCase.execute(null))
                .assertNext(result -> {
                    assertEquals(0L, result.total());
                    assertEquals(0L, result.draft());
                })
                .verifyComplete();
    }

    @Test
    void exposesBoundContext() {
        assertEquals("advertising.admin", useCase.getBoundContext());
    }
}
