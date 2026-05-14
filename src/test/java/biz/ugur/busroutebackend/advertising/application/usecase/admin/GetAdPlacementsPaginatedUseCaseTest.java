package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementResponse;
import biz.ugur.busroutebackend.advertising.application.mapper.AdPlacementResponseMapper;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffId;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetAdPlacementsPaginatedUseCaseTest {

    @InjectMocks
    private GetAdPlacementsPaginatedUseCase useCase;

    @Mock
    private AdPlacementRepository placementRepository;

    @Mock
    private AdPlacementResponseMapper responseMapper;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void returnsAllPlacementsWhenNoFilter() {
        AdPlacement placement = AdPlacement.create(
                BusinessId.generate(), TariffId.generate(), PlacementType.BANNER,
                null, "Title", "content", null, null, null, null, null, 0);
        AdPlacementResponse response = mock(AdPlacementResponse.class);
        when(response.status()).thenReturn("DRAFT");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(placementRepository.findAll(any(Pageable.class))).thenReturn(Flux.just(placement));
        when(placementRepository.count()).thenReturn(Mono.just(1L));
        when(responseMapper.toResponses(List.of(placement))).thenReturn(Flux.just(response));

        StepVerifier.create(useCase.execute(new GetAdPlacementsPaginatedUseCase.Query(1, 10, null, null)))
                .assertNext(list -> {
                    assertEquals(1, list.getPlacements().size());
                    assertEquals(0L, list.getActiveCount());
                })
                .verifyComplete();
    }

    @Test
    void filtersByBusinessId() {
        AdPlacement placement = AdPlacement.create(
                BusinessId.generate(), TariffId.generate(), PlacementType.BANNER,
                null, "Title", "content", null, null, null, null, null, 0);
        AdPlacementResponse response = mock(AdPlacementResponse.class);
        when(response.status()).thenReturn("ACTIVE");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(placementRepository.findByBusinessId(any(BusinessId.class), any(Pageable.class)))
                .thenReturn(Flux.just(placement));
        when(placementRepository.countByBusinessId(any(BusinessId.class))).thenReturn(Mono.just(1L));
        when(responseMapper.toResponses(List.of(placement))).thenReturn(Flux.just(response));

        StepVerifier.create(useCase.execute(new GetAdPlacementsPaginatedUseCase.Query(
                1, 10, null, BusinessId.generate().getValue())))
                .assertNext(list -> {
                    assertEquals(1, list.getPlacements().size());
                    assertEquals(1L, list.getActiveCount());
                })
                .verifyComplete();
    }

    @Test
    void filtersByStatus() {
        AdPlacement placement = AdPlacement.create(
                BusinessId.generate(), TariffId.generate(), PlacementType.BANNER,
                null, "Title", "content", null, null, null, null, null, 0);
        AdPlacementResponse response = mock(AdPlacementResponse.class);
        when(response.status()).thenReturn("DRAFT");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(placementRepository.findByStatus(any(PlacementStatus.class), any(Pageable.class)))
                .thenReturn(Flux.just(placement));
        when(placementRepository.count()).thenReturn(Mono.just(1L));
        when(responseMapper.toResponses(List.of(placement))).thenReturn(Flux.just(response));

        StepVerifier.create(useCase.execute(new GetAdPlacementsPaginatedUseCase.Query(
                1, 10, "draft", null)))
                .assertNext(list -> assertEquals(1, list.getPlacements().size()))
                .verifyComplete();
    }

    @Test
    void exposesBoundContext() {
        assertEquals("advertising.admin", useCase.getBoundContext());
    }
}
