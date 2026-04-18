package biz.ugur.busroutebackend.payment.infrastructure.config;

import biz.ugur.busroutebackend.payment.domain.services.PaymentOrchestrator;
import biz.ugur.busroutebackend.payment.domain.services.PaymentProviderGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class PaymentModuleConfig {

    @Bean
    public PaymentOrchestrator paymentOrchestrator(List<PaymentProviderGateway> gateways) {
        return new PaymentOrchestrator(gateways);
    }
}
