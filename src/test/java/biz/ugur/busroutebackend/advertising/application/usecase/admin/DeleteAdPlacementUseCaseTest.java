package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.domain.enums.ContentType;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdPlacementNotFoundException;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdClickEventRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdImpressionEventRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.storage.AdPlacementStorage;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffId;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class DeleteAdPlacementUseCaseTest {

    @InjectMocks
    private DeleteAdPlacementUseCase useCase;

    @Mock
    private AdPlacementRepository placementRepository;

    @Mock
    private AdPlacementStorage storage;

    @Mock
    private AdImpressionEventRepository impressionEventRepository;

    @Mock
    private AdClickEventRepository clickEventRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    private AdPlacement placementWithImage(String imageUrl) {
        return AdPlacement.create(
                BusinessId.generate(), TariffId.generate(), PlacementType.BANNER,
                null, "Title", null, imageUrl, "https://target", null,
                ContentType.LINK, null, null, 0);
    }

    private void stubCorrelationPassthrough() {
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void deletesPlacementImageAndAnalytics() {
        AdPlacement placement = placementWithImage("/ad-placements/2026/06/original_x.jpg");
        PlacementId id = placement.getId();

        stubCorrelationPassthrough();
        when(placementRepository.findById(id)).thenReturn(Mono.just(placement));
        when(storage.delete("/ad-placements/2026/06/original_x.jpg")).thenReturn(Mono.empty());
        when(impressionEventRepository.deleteByPlacementId(id)).thenReturn(Mono.empty());
        when(clickEventRepository.deleteByPlacementId(id)).thenReturn(Mono.empty());
        when(placementRepository.deleteById(id)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(id.getValue()))
                .verifyComplete();

        verify(storage).delete("/ad-placements/2026/06/original_x.jpg");
        verify(impressionEventRepository).deleteByPlacementId(id);
        verify(clickEventRepository).deleteByPlacementId(id);
        verify(placementRepository).deleteById(id);
    }

    @Test
    void skipsStorageWhenNoImage() {
        AdPlacement placement = placementWithImage(null);
        PlacementId id = placement.getId();

        stubCorrelationPassthrough();
        when(placementRepository.findById(id)).thenReturn(Mono.just(placement));
        when(impressionEventRepository.deleteByPlacementId(id)).thenReturn(Mono.empty());
        when(clickEventRepository.deleteByPlacementId(id)).thenReturn(Mono.empty());
        when(placementRepository.deleteById(id)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(id.getValue()))
                .verifyComplete();

        verify(storage, never()).delete(any());
        verify(placementRepository).deleteById(id);
    }

    @Test
    void errorsWhenPlacementNotFound() {
        String id = PlacementId.generate().getValue();

        stubCorrelationPassthrough();
        when(placementRepository.findById(any(PlacementId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(id))
                .expectErrorSatisfies(err -> assertInstanceOf(AdPlacementNotFoundException.class, err))
                .verify();

        verify(storage, never()).delete(any());
        verify(placementRepository, never()).deleteById(any());
    }

    @Test
    void exposesBoundContext() {
        assertEquals("advertising.admin", useCase.getBoundContext());
    }
}
