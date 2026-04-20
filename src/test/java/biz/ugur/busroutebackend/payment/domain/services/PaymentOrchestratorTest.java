package biz.ugur.busroutebackend.payment.domain.services;

import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentSubjectType;
import biz.ugur.busroutebackend.payment.domain.exceptions.PaymentProviderException;
import biz.ugur.busroutebackend.payment.domain.model.Payment;
import biz.ugur.busroutebackend.payment.domain.services.PaymentProviderGateway.ActionResult;
import biz.ugur.busroutebackend.payment.domain.services.PaymentProviderGateway.RegisterResult;
import biz.ugur.busroutebackend.payment.domain.services.PaymentProviderGateway.StatusResult;
import biz.ugur.busroutebackend.payment.domain.valueobjects.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentOrchestratorTest {

    private PaymentProviderGateway halkGateway;
    private PaymentProviderGateway senagatGateway;
    private Payment payment;

    @BeforeEach
    void setUp() {
        halkGateway = mock(PaymentProviderGateway.class);
        senagatGateway = mock(PaymentProviderGateway.class);

        when(halkGateway.supports(PaymentProvider.HALK)).thenReturn(true);
        when(senagatGateway.supports(PaymentProvider.SENAGAT)).thenReturn(true);

        payment = Payment.register(
                PaymentProvider.HALK,
                PaymentSubjectType.AD_PLACEMENT,
                "subject-1",
                "biz-1",
                Money.ofMinor(100_00L, "TMT"),
                "https://example.com/return",
                LocalDateTime.now().plusHours(1)
        );
    }

    @Test
    void resolveReturnsMatchingGateway() {
        PaymentOrchestrator orchestrator = new PaymentOrchestrator(List.of(halkGateway, senagatGateway));

        assertSame(halkGateway, orchestrator.resolve(PaymentProvider.HALK));
        assertSame(senagatGateway, orchestrator.resolve(PaymentProvider.SENAGAT));
    }

    @Test
    void resolveThrowsWhenNoGatewayMatches() {
        PaymentOrchestrator orchestrator = new PaymentOrchestrator(List.of(senagatGateway));

        PaymentProviderException ex = assertThrows(
                PaymentProviderException.class,
                () -> orchestrator.resolve(PaymentProvider.HALK)
        );
        assertInstanceOf(PaymentProviderException.class, ex);
    }

    @Test
    void registerDelegatesToResolvedGateway() {
        RegisterResult result = new RegisterResult("order-1", "https://pay/form");
        when(halkGateway.register(payment)).thenReturn(Mono.just(result));

        PaymentOrchestrator orchestrator = new PaymentOrchestrator(List.of(halkGateway, senagatGateway));

        StepVerifier.create(orchestrator.register(payment))
                .assertNext(r -> assertEquals("order-1", r.providerOrderId()))
                .verifyComplete();
    }

    @Test
    void getStatusDelegatesToResolvedGateway() {
        StatusResult result = new StatusResult(2, null, null, 100_00L, "****", "12/26", "Name");
        when(halkGateway.getStatus(payment)).thenReturn(Mono.just(result));

        PaymentOrchestrator orchestrator = new PaymentOrchestrator(List.of(halkGateway, senagatGateway));

        StepVerifier.create(orchestrator.getStatus(payment))
                .assertNext(r -> assertEquals(2, r.orderStatusCode()))
                .verifyComplete();
    }

    @Test
    void reverseDelegatesToResolvedGateway() {
        ActionResult result = new ActionResult(true, null, null);
        when(halkGateway.reverse(payment)).thenReturn(Mono.just(result));

        PaymentOrchestrator orchestrator = new PaymentOrchestrator(List.of(halkGateway, senagatGateway));

        StepVerifier.create(orchestrator.reverse(payment))
                .assertNext(r -> assertEquals(true, r.success()))
                .verifyComplete();
    }

    @Test
    void refundDelegatesToResolvedGatewayWithAmount() {
        ActionResult result = new ActionResult(true, null, null);
        when(halkGateway.refund(any(Payment.class), anyLong())).thenReturn(Mono.just(result));

        PaymentOrchestrator orchestrator = new PaymentOrchestrator(List.of(halkGateway, senagatGateway));

        StepVerifier.create(orchestrator.refund(payment, 50_00L))
                .assertNext(r -> assertEquals(true, r.success()))
                .verifyComplete();
    }
}
