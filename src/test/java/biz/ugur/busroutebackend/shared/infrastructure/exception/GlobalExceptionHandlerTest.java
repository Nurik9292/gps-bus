package biz.ugur.busroutebackend.shared.infrastructure.exception;

import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.i18n.LocaleContextResolver;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(
            mock(MessageSource.class),
            mock(LocaleContextResolver.class),
            new ErrorResponseFactory()
    );

    @Test
    void notFoundResponseStatusExceptionKeeps404() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/diagnostics/route-geometry").build());
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND);

        StepVerifier.create(handler.handleResponseStatusException(ex, exchange))
                .assertNext(response -> {
                    assertThat(response.getStatusCode().value()).isEqualTo(404);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().getStatus()).isEqualTo(404);
                })
                .verifyComplete();
    }

    @Test
    void methodNotAllowedKeepsItsOwnStatus() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/anything").build());
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "method not allowed");

        StepVerifier.create(handler.handleResponseStatusException(ex, exchange))
                .assertNext(response -> assertThat(response.getStatusCode().value()).isEqualTo(405))
                .verifyComplete();
    }
}
