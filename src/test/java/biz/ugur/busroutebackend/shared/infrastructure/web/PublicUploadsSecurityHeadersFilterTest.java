package biz.ugur.busroutebackend.shared.infrastructure.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class PublicUploadsSecurityHeadersFilterTest {

    private final PublicUploadsSecurityHeadersFilter filter = new PublicUploadsSecurityHeadersFilter();

    private HttpHeaders runFilterAndCommit(String path) {
        MockServerHttpRequest request = MockServerHttpRequest.get(path).build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        WebFilterChain chain = ex -> Mono.empty();

        filter.filter(exchange, chain).block();
        exchange.getResponse().setComplete().block();

        return exchange.getResponse().getHeaders();
    }

    @Test
    void setsSecurityHeadersForAdPlacementsPath() {
        HttpHeaders headers = runFilterAndCommit("/ad-placements/2026/04/original_xxx.png");

        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("Content-Security-Policy")).contains("default-src 'none'");
        assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("no-referrer");
    }

    @Test
    void setsSecurityHeadersForBannersPath() {
        HttpHeaders headers = runFilterAndCommit("/banners/2026/04/banner.jpg");

        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void setsSecurityHeadersForAvatarsPath() {
        HttpHeaders headers = runFilterAndCommit("/avatars/admin/avatar.png");

        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void setsHeadersForApiV1PrefixedPaths() {
        HttpHeaders headers = runFilterAndCommit("/api/v1/ad-placements/2026/04/file.webp");

        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("Content-Security-Policy")).contains("img-src 'self' data:");
    }

    @Test
    void doesNotSetHeadersForRegularApiPath() {
        HttpHeaders headers = runFilterAndCommit("/api/v1/admin/routes");

        assertThat(headers.getFirst("X-Content-Type-Options")).isNull();
        assertThat(headers.getFirst("Content-Security-Policy")).isNull();
    }

    @Test
    void doesNotSetHeadersForRoot() {
        HttpHeaders headers = runFilterAndCommit("/");

        assertThat(headers.getFirst("X-Content-Type-Options")).isNull();
    }
}
