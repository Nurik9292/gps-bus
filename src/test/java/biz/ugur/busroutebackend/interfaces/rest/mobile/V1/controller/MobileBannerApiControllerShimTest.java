package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.controller;

import biz.ugur.busroutebackend.advertising.application.usecase.mobile.GetActiveBannersAsAdPlacementsUseCase;
import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.banner.application.dto.BannerList;
import biz.ugur.busroutebackend.banner.application.dto.BannerPaginationQuery;
import biz.ugur.busroutebackend.banner.application.dto.BannerResponse;
import biz.ugur.busroutebackend.client.application.usecase.RouteIsFavoriteUseCase;
import biz.ugur.busroutebackend.shared.infrastructure.config.CorrelationConfig;
import biz.ugur.busroutebackend.shared.infrastructure.config.MessageSourceConfig;
import biz.ugur.busroutebackend.shared.infrastructure.exception.ErrorResponseFactory;
import biz.ugur.busroutebackend.shared.infrastructure.exception.GlobalExceptionHandler;
import biz.ugur.busroutebackend.shared.infrastructure.exception.handlers.BannerExceptionHandler;
import biz.ugur.busroutebackend.shared.infrastructure.exception.handlers.ValidationExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = MobileBannerApiController.class)
@Import({MessageSourceConfig.class, CorrelationConfig.class, ErrorResponseFactory.class,
        ValidationExceptionHandler.class, BannerExceptionHandler.class, GlobalExceptionHandler.class})
@WithMockUser
class MobileBannerApiControllerShimTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private GetActiveBannersAsAdPlacementsUseCase useCase;

    @MockBean
    private RouteIsFavoriteUseCase routeIsFavoriteUseCase;

    @Test
    void get_all_banners_returns_banner_list() {
        BannerList list = BannerList.of(
                List.of(sampleBanner("b-1", "main")),
                1L, 1, 10, 1L);
        when(useCase.executeAll()).thenReturn(Mono.just(list));

        webTestClient.get().uri("/api/v1/mobile/banners")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.banners").isArray()
                .jsonPath("$.data.banners[0].id").isEqualTo("b-1")
                .jsonPath("$.data.banners[0].type").isEqualTo("main")
                .jsonPath("$.data.activeCount").isEqualTo(1)
                .jsonPath("$.data.pagination").exists();
    }

    @Test
    void get_banners_by_type_returns_banner_list() {
        BannerList list = BannerList.of(
                List.of(sampleBanner("b-2", "main")),
                1L, 1, 10, 1L);
        when(useCase.execute(eq(TargetType.HOME))).thenReturn(Mono.just(list));

        webTestClient.get().uri("/api/v1/mobile/banners/type/main")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.banners[0].id").isEqualTo("b-2")
                .jsonPath("$.data.banners[0].type").isEqualTo("main")
                .jsonPath("$.data.banners[0].target_url").isEqualTo("https://e.com")
                .jsonPath("$.data.banners[0].is_active").isEqualTo(true)
                .jsonPath("$.data.banners[0].display_order").isEqualTo(0);
    }

    @Test
    void get_banners_paginated_returns_banner_list() {
        BannerList list = BannerList.of(
                List.of(sampleBanner("b-3", "main")),
                1L, 1, 10, 1L);
        when(useCase.executePaginated(any(BannerPaginationQuery.class))).thenReturn(Mono.just(list));

        webTestClient.get().uri("/api/v1/mobile/banners/paginated?page=1&size=10&type=main")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.banners[0].id").isEqualTo("b-3")
                .jsonPath("$.data.pagination.current_page").isEqualTo(1)
                .jsonPath("$.data.pagination.page_size").isEqualTo(10)
                .jsonPath("$.data.pagination.total_items").isEqualTo(1);
    }

    @Test
    void get_banners_by_type_stop_button_returns_400() {
        webTestClient.get().uri("/api/v1/mobile/banners/type/stop-button")
                .exchange()
                .expectStatus().is4xxClientError();
    }

    @Test
    void get_banners_by_type_unknown_returns_400() {
        webTestClient.get().uri("/api/v1/mobile/banners/type/foo")
                .exchange()
                .expectStatus().is4xxClientError();
    }

    private BannerResponse sampleBanner(String id, String type) {
        return new BannerResponse(
                id,
                "title",
                type,
                "/img.jpg",
                "https://e.com",
                true,
                0,
                LocalDateTime.now(),
                null,
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now());
    }
}
