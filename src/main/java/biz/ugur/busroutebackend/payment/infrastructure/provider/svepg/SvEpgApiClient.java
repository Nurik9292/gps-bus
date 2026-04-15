package biz.ugur.busroutebackend.payment.infrastructure.provider.svepg;

import biz.ugur.busroutebackend.payment.domain.exceptions.PaymentProviderException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * Low-level HTTP client for the SmartVista EPG REST API (sv_epg).
 *
 * <p>All endpoints accept form-encoded {@code userName} + {@code password} on every call.
 * Responses are JSON. One client instance serves all configured banks — credentials are
 * passed per request via {@link SvEpgCredentials}.
 */
@Component
@Slf4j
public class SvEpgApiClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public SvEpgApiClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /** POST /register.do — registers a one-phase order. */
    public Mono<JsonNode> register(SvEpgCredentials creds,
                                    String orderNumber,
                                    long amountMinor,
                                    int currencyIso4217,
                                    String returnUrl,
                                    String description) {
        MultiValueMap<String, String> form = baseForm(creds);
        form.add("orderNumber", orderNumber);
        form.add("amount", String.valueOf(amountMinor));
        form.add("currency", String.valueOf(currencyIso4217));
        form.add("returnUrl", returnUrl);
        if (description != null && !description.isBlank()) {
            form.add("description", description);
        }
        return post(creds, "/register.do", form);
    }

    /** POST /getOrderStatus.do — returns current order state. */
    public Mono<JsonNode> getOrderStatus(SvEpgCredentials creds, String providerOrderId) {
        MultiValueMap<String, String> form = baseForm(creds);
        form.add("orderId", providerOrderId);
        return post(creds, "/getOrderStatus.do", form);
    }

    /** POST /reverse.do — cancel an authorized payment. */
    public Mono<JsonNode> reverse(SvEpgCredentials creds, String providerOrderId) {
        MultiValueMap<String, String> form = baseForm(creds);
        form.add("orderId", providerOrderId);
        return post(creds, "/reverse.do", form);
    }

    /** POST /refund.do — refund a completed payment. */
    public Mono<JsonNode> refund(SvEpgCredentials creds, String providerOrderId, long amountMinor) {
        MultiValueMap<String, String> form = baseForm(creds);
        form.add("orderId", providerOrderId);
        form.add("amount", String.valueOf(amountMinor));
        return post(creds, "/refund.do", form);
    }

    // -------------------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------------------

    private static MultiValueMap<String, String> baseForm(SvEpgCredentials creds) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("userName", creds.userName());
        form.add("password", creds.password());
        return form;
    }

    private Mono<JsonNode> post(SvEpgCredentials creds,
                                 String path,
                                 MultiValueMap<String, String> form) {
        String url = creds.baseUrl() + path;
        log.debug("[sv_epg] POST {} (user={})", url, creds.userName());

        return webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseJson)
                .timeout(Duration.ofSeconds(20))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(500))
                        .filter(this::isTransient)
                        .doBeforeRetry(sig -> log.warn("[sv_epg] Retry {} for {}: {}",
                                sig.totalRetries() + 1, path, sig.failure().getMessage())))
                .onErrorMap(e -> !(e instanceof PaymentProviderException),
                        e -> new PaymentProviderException("sv_epg request failed", e));
    }

    private JsonNode parseJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("[sv_epg] Malformed JSON response: {}", body);
            throw new PaymentProviderException("malformed sv_epg response", e);
        }
    }

    private boolean isTransient(Throwable t) {
        if (t instanceof java.util.concurrent.TimeoutException) return true;
        if (t instanceof java.io.IOException) return true;
        return false;
    }
}
