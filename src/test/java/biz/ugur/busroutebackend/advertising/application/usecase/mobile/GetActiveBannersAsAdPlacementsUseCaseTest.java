package biz.ugur.busroutebackend.advertising.application.usecase.mobile;

import biz.ugur.busroutebackend.advertising.domain.enums.ContentType;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementKind;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementTarget;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementWindow;
import biz.ugur.busroutebackend.interfaces.rest.mobile.V1.dto.BannerResponse;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetActiveBannersAsAdPlacementsUseCaseTest {

    @Mock private AdPlacementRepository placementRepository;
    @Mock private CorrelationContextService correlationService;
    @Mock private EventBus eventBus;

    @InjectMocks private GetActiveBannersAsAdPlacementsUseCase useCase;

    @BeforeEach
    void setup() {
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void returns_active_editorial_placements_mapped_to_banner_response() {
        AdPlacement p = stubPlacement(TargetType.HOME, ContentType.LINK, "https://e.com", null);
        when(placementRepository.findActiveByKindAndTargetType(PlacementKind.EDITORIAL, TargetType.HOME))
                .thenReturn(Flux.just(p));

        StepVerifier.create(useCase.execute(TargetType.HOME))
                .assertNext(list -> {
                    assertEquals(1, list.size());
                    BannerResponse r = list.get(0);
                    assertEquals("main", r.type());
                    assertEquals("https://e.com", r.targetUrl());
                    assertNull(r.content());
                    assertTrue(r.isActive());
                })
                .verifyComplete();
    }

    @Test
    void empty_list_when_no_placements() {
        when(placementRepository.findActiveByKindAndTargetType(PlacementKind.EDITORIAL, TargetType.POPUP))
                .thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(TargetType.POPUP))
                .assertNext(list -> assertTrue(list.isEmpty()))
                .verifyComplete();
    }

    private AdPlacement stubPlacement(TargetType target, ContentType ct, String targetUrl, String content) {
        return AdPlacement.restore(
                PlacementId.of("p-1"),
                null,
                null,
                PlacementType.BANNER,
                PlacementKind.EDITORIAL,
                PlacementStatus.ACTIVE,
                "title",
                content,
                "/img.jpg",
                targetUrl,
                "CTA",
                ct,
                PlacementWindow.unscheduled(),
                List.of(PlacementTarget.of(target, null)),
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0L);
    }
}
