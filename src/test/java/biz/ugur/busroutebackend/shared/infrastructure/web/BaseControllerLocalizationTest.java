package biz.ugur.busroutebackend.shared.infrastructure.web;

import biz.ugur.busroutebackend.shared.domain.exception.AbstractDomainException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Locale;

@WebFluxTest
@Import({TestLocalizationController.class, biz.ugur.busroutebackend.shared.infrastructure.config.MessageSourceConfig.class})
class BaseControllerLocalizationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void testErrorLocalizationEnglish() {
        webTestClient.get()
                .uri("/test/error")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorMessage").isEqualTo("Invalid request parameters");
    }

    @Test
    void testErrorLocalizationRussian() {
        webTestClient.get()
                .uri("/test/error")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "ru")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorMessage").isEqualTo("Неверные параметры запроса");
    }

    @Test
    void testErrorLocalizationTurkmen() {
        webTestClient.get()
                .uri("/test/error")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "tk")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorMessage").isEqualTo("Ýalňyş sorag parametrleri");
    }

    @Test
    void testSuccessMessage() {
        webTestClient.get()
                .uri("/test/success")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").isEqualTo("Test successful");
    }
}

@RestController
class TestLocalizationController extends BaseController {

    public TestLocalizationController(MessageSource messageSource) {
        super(messageSource);
    }

    @Override
    protected String getControllerName() {
        return "TestLocalizationController";
    }

    @GetMapping("/test/error")
    public Mono<ResponseEntity<BaseController.ApiResponse<String>>> testError() {
        throw new TestDomainException("INVALID_REQUEST", "Test error");
    }

    @GetMapping("/test/success")
    public Mono<ResponseEntity<BaseController.ApiResponse<String>>> testSuccess() {
        return ok(Mono.just("Test successful"));
    }
}

class TestDomainException extends AbstractDomainException {
    public TestDomainException(String errorCode, String message) {
        super(errorCode, message);
    }
}