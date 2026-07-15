package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.controller;

import biz.ugur.busroutebackend.catalogsearch.application.dto.CatalogSearchResult;
import biz.ugur.busroutebackend.catalogsearch.application.usecase.SearchCatalogUseCase;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.shared.infrastructure.correlation.CorrelationIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import biz.ugur.busroutebackend.shared.infrastructure.exception.ErrorResponseFactory;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MobileSearchControllerFlagTest {

    @TestConfiguration
    static class StubUseCaseConfig {

        @Bean
        SearchCatalogUseCase searchCatalogUseCase() {
            SearchCatalogUseCase useCase = mock(SearchCatalogUseCase.class);
            when(useCase.execute(any(Mono.class))).thenReturn(Mono.just(
                    new CatalogSearchResult("berkar", true, List.of())));
            return useCase;
        }
    }

    @Nested
    @WebFluxTest(controllers = MobileSearchController.class,
            excludeAutoConfiguration = ReactiveSecurityAutoConfiguration.class)
    @TestPropertySource(properties = "ugur.catalog-search.enabled=true")
    @Import(StubUseCaseConfig.class)
    class EnabledContext {

        @Autowired
        private WebTestClient webTestClient;

        @MockitoBean
        private ErrorResponseFactory errorResponseFactory;

        @MockitoBean
        private CorrelationIdGenerator correlationIdGenerator;

        @BeforeEach
        void stubCorrelation() {
            when(correlationIdGenerator.extractOrGenerate(any()))
                    .thenReturn(CorrelationId.generate());
        }

        @Test
        void searchRespondsOkWhenFlagEnabled() {
            webTestClient.get().uri("/api/v1/mobile/search?q=berkar")
                    .exchange()
                    .expectStatus().isOk();
        }
    }

    @Nested
    @WebFluxTest(controllers = MobileSearchController.class,
            excludeAutoConfiguration = ReactiveSecurityAutoConfiguration.class)
    @TestPropertySource(properties = "ugur.catalog-search.enabled=false")
    class DisabledContext {

        @Autowired
        private WebTestClient webTestClient;

        @MockitoBean
        private ErrorResponseFactory errorResponseFactory;

        @MockitoBean
        private CorrelationIdGenerator correlationIdGenerator;

        @BeforeEach
        void stubCorrelation() {
            when(correlationIdGenerator.extractOrGenerate(any()))
                    .thenReturn(CorrelationId.generate());
        }

        @Test
        void searchRespondsNotFoundWhenFlagDisabled() {
            webTestClient.get().uri("/api/v1/mobile/search?q=berkar")
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }
}
