package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementResponse;
import biz.ugur.busroutebackend.advertising.application.dto.RejectAdPlacementCommand;
import biz.ugur.busroutebackend.advertising.application.mapper.AdPlacementResponseMapper;
import biz.ugur.busroutebackend.advertising.domain.enums.ContentType;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdPlacementNotFoundException;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffId;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class RejectAdPlacementUseCaseTest {

    @InjectMocks
    private RejectAdPlacementUseCase useCase;

    @Mock
    private AdPlacementRepository placementRepository;

    @Mock
    private AdPlacementResponseMapper responseMapper;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    private AdPlacement placement;

    @BeforeEach
    void setUp() {
        placement = AdPlacement.create(
                BusinessId.generate(), TariffId.generate(), PlacementType.BANNER,
                null, "Title", null, null, "https://target", null,
                ContentType.LINK, null, null, 0);
    }

    @Test
    void rejectsDraftPlacementWithReason() {
        AdPlacementResponse response = mock(AdPlacementResponse.class);
        when(response.id()).thenReturn(placement.getId().getValue());

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(placementRepository.findById(placement.getId())).thenReturn(Mono.just(placement));
        when(placementRepository.save(any(AdPlacement.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(responseMapper.toResponse(any(AdPlacement.class))).thenReturn(Mono.just(response));

        StepVerifier.create(useCase.execute(new RejectAdPlacementUseCase.Request(
                placement.getId().getValue(), "admin-1", new RejectAdPlacementCommand("not compliant"))))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void errorsWhenPlacementNotFound() {
        String id = PlacementId.generate().getValue();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(placementRepository.findById(any(PlacementId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(new RejectAdPlacementUseCase.Request(
                id, "admin-1", new RejectAdPlacementCommand("reason"))))
                .expectErrorSatisfies(err -> assertInstanceOf(AdPlacementNotFoundException.class, err))
                .verify();
    }

    @Test
    void exposesBoundContext() {
        assertEquals("advertising.admin", useCase.getBoundContext());
    }
}
