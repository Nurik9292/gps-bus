package biz.ugur.busroutebackend.payment.infrastructure.config;

import biz.ugur.busroutebackend.payment.domain.services.PaymentOrchestrator;
import biz.ugur.busroutebackend.payment.domain.services.PaymentProviderGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration anchor for the payment bounded context.
 *
 * <p>Registers {@link PaymentOrchestrator} with all auto-discovered
 * {@link PaymentProviderGateway} beans. Adding a new gateway (e.g. non-sv_epg bank)
 * is a pure add — no wiring here changes.
 */
@Configuration
public class PaymentModuleConfig {

    @Bean
    public PaymentOrchestrator paymentOrchestrator(List<PaymentProviderGateway> gateways) {
        return new PaymentOrchestrator(gateways);
    }
}
