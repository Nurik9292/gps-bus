package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.controller;

import biz.ugur.busroutebackend.advertising.application.dto.RecordClickCommand;
import biz.ugur.busroutebackend.advertising.application.dto.RecordDetailViewCommand;
import biz.ugur.busroutebackend.advertising.application.dto.RecordImpressionCommand;
import biz.ugur.busroutebackend.advertising.application.usecase.client.GetActiveAdsUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.client.GetActiveTariffsUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.client.RecordClickUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.client.RecordDetailViewUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.client.RecordImpressionUseCase;
import biz.ugur.busroutebackend.shared.infrastructure.config.CorrelationConfig;
import biz.ugur.busroutebackend.shared.infrastructure.config.MessageSourceConfig;
import biz.ugur.busroutebackend.shared.infrastructure.exception.ErrorResponseFactory;
import biz.ugur.busroutebackend.shared.infrastructure.exception.GlobalExceptionHandler;
import biz.ugur.busroutebackend.shared.infrastructure.exception.handlers.ValidationExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;

@WebFluxTest(controllers = MobileAdController.class)
@Import({MessageSourceConfig.class, CorrelationConfig.class, ErrorResponseFactory.class,
        ValidationExceptionHandler.class, GlobalExceptionHandler.class})
@WithMockUser
class MobileAdControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private GetActiveAdsUseCase getActiveAdsUseCase;
    @MockBean
    private GetActiveTariffsUseCase getActiveTariffsUseCase;
    @MockBean
    private RecordImpressionUseCase recordImpressionUseCase;
    @MockBean
    private RecordClickUseCase recordClickUseCase;
    @MockBean
    private RecordDetailViewUseCase recordDetailViewUseCase;

    @Test
    void postImpression_withNullBody_returns204() {
        when(recordImpressionUseCase.execute(any(RecordImpressionCommand.class))).thenReturn(Mono.empty());

        var placementId = UUID.randomUUID().toString();
        webTestClient.mutateWith(csrf()).post()
                .uri("/api/v1/mobile/ads/" + placementId + "/impression")
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void postImpression_withTargetTypeHome_returns204() {
        when(recordImpressionUseCase.execute(any(RecordImpressionCommand.class))).thenReturn(Mono.empty());

        var placementId = UUID.randomUUID().toString();
        webTestClient.mutateWith(csrf()).post()
                .uri("/api/v1/mobile/ads/" + placementId + "/impression")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"targetType\":\"HOME\"}")
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void postImpression_withTargetIdButNoType_returns400() {
        var placementId = UUID.randomUUID().toString();
        webTestClient.mutateWith(csrf()).post()
                .uri("/api/v1/mobile/ads/" + placementId + "/impression")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"targetId\":\"" + UUID.randomUUID() + "\"}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void postClick_validBody_returns204() {
        when(recordClickUseCase.execute(any(RecordClickCommand.class))).thenReturn(Mono.empty());

        var placementId = UUID.randomUUID().toString();
        webTestClient.mutateWith(csrf()).post()
                .uri("/api/v1/mobile/ads/" + placementId + "/click")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"targetType\":\"POPUP\"}")
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void postDetailView_withoutDurationMs_returns400() {
        var placementId = UUID.randomUUID().toString();
        webTestClient.mutateWith(csrf()).post()
                .uri("/api/v1/mobile/ads/" + placementId + "/detail-view")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void postDetailView_withDurationOverCap_returns400() {
        var placementId = UUID.randomUUID().toString();
        webTestClient.mutateWith(csrf()).post()
                .uri("/api/v1/mobile/ads/" + placementId + "/detail-view")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"durationMs\":1800001}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void postDetailView_validBody_returns204() {
        when(recordDetailViewUseCase.execute(any(RecordDetailViewCommand.class))).thenReturn(Mono.empty());

        var placementId = UUID.randomUUID().toString();
        webTestClient.mutateWith(csrf()).post()
                .uri("/api/v1/mobile/ads/" + placementId + "/detail-view")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"durationMs\":12500,\"targetType\":\"POPUP\"}")
                .exchange()
                .expectStatus().isNoContent();
    }
}
