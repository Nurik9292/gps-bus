package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementResponse;
import biz.ugur.busroutebackend.advertising.application.dto.CreateAdPlacementCommand;
import biz.ugur.busroutebackend.advertising.application.factory.AdPlacementFactory;
import biz.ugur.busroutebackend.advertising.application.mapper.AdPlacementResponseMapper;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementKind;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementTargetRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementTarget;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffId;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class CreateAdPlacementUseCaseTest {

    @InjectMocks
    private CreateAdPlacementUseCase useCase;

    @Mock
    private AdPlacementRepository placementRepository;

    @Mock
    private AdPlacementTargetRepository targetRepository;

    @Mock
    private AdPlacementFactory placementFactory;

    @Mock
    private AdPlacementResponseMapper responseMapper;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void createsPlacementAndReturnsResponse() {
        CreateAdPlacementCommand cmd = mock(CreateAdPlacementCommand.class);
        AdPlacement placement = AdPlacement.create(
                BusinessId.generate(), TariffId.generate(), PlacementType.BANNER,
                null, "Title", "content", null, null, null, null, null, 0);
        AdPlacementResponse response = mock(AdPlacementResponse.class);
        when(response.id()).thenReturn(placement.getId().getValue());
        when(response.businessId()).thenReturn(placement.getBusinessId().getValue());

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(placementFactory.create(cmd)).thenReturn(Mono.just(placement));
        when(placementRepository.save(placement)).thenReturn(Mono.just(placement));
        when(targetRepository.replaceAll(placement.getId(), placement.getTargets()))
                .thenReturn(Mono.empty());
        when(responseMapper.toResponse(placement)).thenReturn(Mono.just(response));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .assertNext(r -> assertEquals(placement.getId().getValue(), r.id()))
                .verifyComplete();
    }

    @Test
    void persistsTargetsAfterPlacementSaved() {
        CreateAdPlacementCommand cmd = mock(CreateAdPlacementCommand.class);
        AdPlacement placement = AdPlacement.create(
                BusinessId.generate(), TariffId.generate(), PlacementType.BANNER,
                PlacementKind.COMMERCIAL, "Promo", null, null, null, null, null,
                java.util.List.of(
                        PlacementTarget.general(TargetType.HOME),
                        PlacementTarget.specific(TargetType.ROUTE, "route-14")),
                0);
        AdPlacementResponse response = mock(AdPlacementResponse.class);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(placementFactory.create(cmd)).thenReturn(Mono.just(placement));
        when(placementRepository.save(placement)).thenReturn(Mono.just(placement));
        when(targetRepository.replaceAll(placement.getId(), placement.getTargets()))
                .thenReturn(Mono.empty());
        when(responseMapper.toResponse(placement)).thenReturn(Mono.just(response));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectNext(response)
                .verifyComplete();

        org.mockito.Mockito.verify(targetRepository)
                .replaceAll(placement.getId(), placement.getTargets());
    }

    @Test
    void surfacesValidationErrorFromFactory() {
        CreateAdPlacementCommand cmd = mock(CreateAdPlacementCommand.class);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(placementFactory.create(cmd))
                .thenReturn(Mono.error(new AdvertisingValidationException(
                        "targetId", "must not be blank for targetType ROUTE")));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectErrorSatisfies(err -> org.junit.jupiter.api.Assertions
                        .assertInstanceOf(AdvertisingValidationException.class, err))
                .verify();
    }

    @Test
    void exposesBoundContext() {
        assertEquals("advertising.admin", useCase.getBoundContext());
    }
}
