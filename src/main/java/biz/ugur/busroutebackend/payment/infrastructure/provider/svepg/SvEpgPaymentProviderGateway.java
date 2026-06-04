package biz.ugur.busroutebackend.payment.infrastructure.provider.svepg;

import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.exceptions.PaymentProviderException;
import biz.ugur.busroutebackend.payment.domain.model.Payment;
import biz.ugur.busroutebackend.payment.domain.services.PaymentProviderGateway;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Set;


@Service
@Slf4j
public class SvEpgPaymentProviderGateway implements PaymentProviderGateway {

    private static final Set<PaymentProvider> SUPPORTED =
            Set.of(PaymentProvider.RYSGAL, PaymentProvider.SENAGAT,
                    PaymentProvider.BKB,    PaymentProvider.HALK);

    private final SvEpgApiClient client;
    private final SvEpgProperties properties;

    public SvEpgPaymentProviderGateway(SvEpgApiClient client, SvEpgProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public boolean supports(PaymentProvider provider) {
        return SUPPORTED.contains(provider) && properties.isEnabled(provider);
    }

    @Override
    public Mono<RegisterResult> register(Payment payment) {
        SvEpgCredentials creds = properties.credentialsFor(payment.getProvider());
        return client.register(
                        creds,
                        payment.getOrderNumber().getValue(),
                        payment.getMoney().getAmountMinor(),
                        payment.getMoney().getIso4217NumericCode(),
                        payment.getReturnUrl(),
                        payment.getFailUrl(),
                        descriptionFor(payment))
                .map(json -> {
                    requireNoError(json);
                    String orderId = textOrNull(json, "orderId");
                    String formUrl = textOrNull(json, "formUrl");
                    if (orderId == null || formUrl == null) {
                        throw new PaymentProviderException(textOrNull(json, "errorCode"),
                                "sv_epg register.do missing orderId/formUrl: " + json);
                    }
                    log.info("[sv_epg] Registered payment={} orderId={} provider={}",
                            payment.getId().getValue(), orderId, payment.getProvider());
                    return new RegisterResult(orderId, formUrl);
                });
    }

    @Override
    public Mono<StatusResult> getStatus(Payment payment) {
        String providerOrderId = payment.getProviderOrderId();
        if (providerOrderId == null) {
            return Mono.error(new PaymentProviderException(null,
                    "cannot query status: payment has no provider order id yet"));
        }
        SvEpgCredentials creds = properties.credentialsFor(payment.getProvider());
        return client.getOrderStatus(creds, providerOrderId)
                .map(json -> {
                    int orderStatusCode = json.path("OrderStatus").asInt(-1);
                    String errorCode = textOrNull(json, "ErrorCode");
                    String errorMessage = textOrNull(json, "ErrorMessage");
                    long amount = json.path("Amount").asLong(0L);
                    String pan = textOrNull(json, "Pan");
                    String expiration = textOrNull(json, "expiration");
                    String cardholderName = textOrNull(json, "cardholderName");
                    return new StatusResult(orderStatusCode, errorCode, errorMessage,
                            amount, pan, expiration, cardholderName);
                });
    }

    @Override
    public Mono<ActionResult> reverse(Payment payment) {
        SvEpgCredentials creds = properties.credentialsFor(payment.getProvider());
        return client.reverse(creds, payment.getProviderOrderId())
                .map(SvEpgPaymentProviderGateway::toActionResult);
    }

    @Override
    public Mono<ActionResult> refund(Payment payment, long amountMinor) {
        SvEpgCredentials creds = properties.credentialsFor(payment.getProvider());
        return client.refund(creds, payment.getProviderOrderId(), amountMinor)
                .map(SvEpgPaymentProviderGateway::toActionResult);
    }

    // -------------------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------------------

    private static String descriptionFor(Payment payment) {
        String subject = switch (payment.getSubjectType()) {
            case AD_PLACEMENT -> "Ad placement";
            case CLIENT_SUBSCRIPTION -> "Client subscription";
        };
        return subject + ": " + payment.getSubjectId();
    }

    private static ActionResult toActionResult(JsonNode json) {
        String errorCode = textOrNull(json, "errorCode");
        String errorMessage = textOrNull(json, "errorMessage");
        boolean success = "0".equals(errorCode) || errorCode == null;
        return new ActionResult(success, errorCode, errorMessage);
    }

    private static void requireNoError(JsonNode json) {
        String code = textOrNull(json, "errorCode");
        if (code != null && !"0".equals(code)) {
            throw new PaymentProviderException(code,
                    "sv_epg error " + code + ": " + textOrNull(json, "errorMessage"));
        }
    }

    private static String textOrNull(JsonNode json, String field) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) return null;
        String txt = node.asText();
        return txt.isEmpty() ? null : txt;
    }
}
