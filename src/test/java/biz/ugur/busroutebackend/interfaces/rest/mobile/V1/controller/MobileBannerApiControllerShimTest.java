package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.controller;

import biz.ugur.busroutebackend.advertising.application.usecase.mobile.GetActiveBannersAsAdPlacementsUseCase;
import biz.ugur.busroutebackend.interfaces.rest.mobile.V1.dto.BannerResponse;
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

    @Test
    void get_banners_main_returns_list() {
        when(useCase.execute(any())).thenReturn(Mono.just(List.of(
                new BannerResponse("b-1", "title", "main", "/img.jpg",
                        "https://e.com", null, true, 0,
                        LocalDateTime.now(), null))));

        webTestClient.get().uri("/api/v1/mobile/banners?type=main")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data[0].id").isEqualTo("b-1")
                .jsonPath("$.data[0].type").isEqualTo("main")
                .jsonPath("$.data[0].target_url").isEqualTo("https://e.com")
                .jsonPath("$.data[0].is_active").isEqualTo(true)
                .jsonPath("$.data[0].display_order").isEqualTo(0);
    }

    @Test
    void get_banners_stop_button_returns_400() {
        webTestClient.get().uri("/api/v1/mobile/banners?type=stop-button")
                .exchange()
                .expectStatus().is4xxClientError();
    }

    @Test
    void get_banners_unknown_type_returns_400() {
        webTestClient.get().uri("/api/v1/mobile/banners?type=foo")
                .exchange()
                .expectStatus().is4xxClientError();
    }

    @Test
    void get_banners_missing_type_returns_400() {
        webTestClient.get().uri("/api/v1/mobile/banners")
                .exchange()
                .expectStatus().is4xxClientError();
    }
}
