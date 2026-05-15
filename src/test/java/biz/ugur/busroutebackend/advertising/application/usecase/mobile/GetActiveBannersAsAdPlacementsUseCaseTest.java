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
import biz.ugur.busroutebackend.banner.application.dto.BannerList;
import biz.ugur.busroutebackend.banner.application.dto.BannerPaginationQuery;
import biz.ugur.busroutebackend.banner.application.dto.BannerResponse;
import biz.ugur.busroutebackend.banner.domain.exceptions.BannerValidationException;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    void returns_active_editorial_placements_mapped_to_banner_list() {
        AdPlacement p = stubPlacement("p-1", ContentType.LINK, "https://e.com", null);
        when(placementRepository.findActiveByKindAndTargetType(PlacementKind.EDITORIAL, TargetType.HOME))
                .thenReturn(Flux.just(p));

        StepVerifier.create(useCase.execute(TargetType.HOME))
                .assertNext(list -> {
                    assertEquals(1, list.items().size());
                    BannerResponse r = list.items().get(0);
                    assertEquals("main", r.type());
                    assertEquals("https://e.com", r.targetUrl());
                    assertNull(r.content());
                    assertTrue(r.isActive());
                    assertEquals(1L, list.activeCount());
                    assertEquals(1, list.pagination().getCurrentPage());
                })
                .verifyComplete();
    }

    @Test
    void empty_banner_list_when_no_placements() {
        when(placementRepository.findActiveByKindAndTargetType(PlacementKind.EDITORIAL, TargetType.POPUP))
                .thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(TargetType.POPUP))
                .assertNext(list -> {
                    assertTrue(list.items().isEmpty());
                    assertEquals(0L, list.activeCount());
                })
                .verifyComplete();
    }

    @Test
    void execute_all_fans_out_across_editorial_target_types() {
        when(placementRepository.findActiveByKindAndTargetType(eq(PlacementKind.EDITORIAL), any(TargetType.class)))
                .thenReturn(Flux.empty());
        when(placementRepository.findActiveByKindAndTargetType(PlacementKind.EDITORIAL, TargetType.HOME))
                .thenReturn(Flux.just(stubPlacement("p-home", ContentType.LINK, "https://e.com", null)));
        when(placementRepository.findActiveByKindAndTargetType(PlacementKind.EDITORIAL, TargetType.POPUP))
                .thenReturn(Flux.just(stubPlacement("p-popup", ContentType.CONTENT, null, "popup content")));

        StepVerifier.create(useCase.executeAll())
                .assertNext(list -> {
                    assertEquals(2, list.items().size());
                    assertEquals(2L, list.activeCount());
                })
                .verifyComplete();
    }

    @Test
    void execute_paginated_returns_slice_with_pagination_metadata() {
        AdPlacement p1 = stubPlacement("p-1", ContentType.LINK, "https://1.com", null);
        AdPlacement p2 = stubPlacement("p-2", ContentType.LINK, "https://2.com", null);
        AdPlacement p3 = stubPlacement("p-3", ContentType.LINK, "https://3.com", null);
        when(placementRepository.findActiveByKindAndTargetType(PlacementKind.EDITORIAL, TargetType.HOME))
                .thenReturn(Flux.just(p1, p2, p3));

        BannerPaginationQuery query = BannerPaginationQuery.createWithType(
                1, 2, "display_order", "asc", true, "main");

        StepVerifier.create(useCase.executePaginated(query))
                .assertNext(list -> {
                    assertEquals(2, list.items().size());
                    assertEquals(3L, list.activeCount());
                    assertEquals(1, list.pagination().getCurrentPage());
                    assertEquals(2, list.pagination().getPageSize());
                    assertEquals(3L, list.pagination().getTotalItems());
                })
                .verifyComplete();
    }

    @Test
    void execute_paginated_rejects_stop_button_type() {
        BannerPaginationQuery query = BannerPaginationQuery.createWithType(
                1, 10, "display_order", "asc", true, "stop-button");

        StepVerifier.create(useCase.executePaginated(query))
                .expectErrorSatisfies(err -> assertInstanceOf(BannerValidationException.class, err))
                .verify();
    }

    private AdPlacement stubPlacement(String id, ContentType ct, String targetUrl, String content) {
        return AdPlacement.restore(
                PlacementId.of(id),
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
                List.of(PlacementTarget.of(TargetType.HOME, null)),
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
