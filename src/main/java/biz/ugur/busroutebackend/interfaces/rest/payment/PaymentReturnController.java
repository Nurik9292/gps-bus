package biz.ugur.busroutebackend.interfaces.rest.payment;

import biz.ugur.busroutebackend.payment.application.dto.PaymentResponse;
import biz.ugur.busroutebackend.payment.application.usecase.admin.SyncPaymentStatusUseCase;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.exceptions.PaymentNotFoundException;
import biz.ugur.busroutebackend.payment.domain.model.Payment;
import biz.ugur.busroutebackend.payment.domain.repository.PaymentRepository;
import biz.ugur.busroutebackend.payment.infrastructure.persistence.repository.PaymentCallbackLogRepository;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_PAYMENTS_RETURN;


@RestController
@RequestMapping(V1_PAYMENTS_RETURN)
@Slf4j
public class PaymentReturnController extends BaseController {

    private final PaymentRepository paymentRepository;
    private final SyncPaymentStatusUseCase syncPaymentStatusUseCase;
    private final PaymentCallbackLogRepository callbackLogRepository;

    public PaymentReturnController(PaymentRepository paymentRepository,
                                    SyncPaymentStatusUseCase syncPaymentStatusUseCase,
                                    PaymentCallbackLogRepository callbackLogRepository,
                                    MessageSource messageSource) {
        super(messageSource);
        this.paymentRepository = paymentRepository;
        this.syncPaymentStatusUseCase = syncPaymentStatusUseCase;
        this.callbackLogRepository = callbackLogRepository;
    }

    @Override
    protected String getControllerName() {
        return PaymentReturnController.class.getSimpleName();
    }

   
    @GetMapping("/{provider}")
    public Mono<ResponseEntity<ApiResponse<PaymentResponse>>> onReturn(
            @PathVariable String provider,
            @RequestParam(value = "orderId", required = false) String orderId,
            @RequestParam(value = "orderNumber", required = false) String orderNumber) {

        PaymentProvider paymentProvider = PaymentProvider.from(provider);

        log.info("[payment-return] provider={} orderId={} orderNumber={}",
                paymentProvider, orderId, orderNumber);

        return lookup(paymentProvider, orderId, orderNumber)
                .switchIfEmpty(Mono.error(new PaymentNotFoundException(
                        "orderId=" + orderId + " / orderNumber=" + orderNumber)))
                .flatMap(payment -> callbackLogRepository.log(
                                payment.getId().getValue(),
                                paymentProvider.name(),
                                "RETURN_URL",
                                payment.getProviderOrderId(),
                                null, null, null, null, null)
                        .thenReturn(payment))
                .flatMap(payment -> syncPaymentStatusUseCase.execute(payment.getId().getValue()))
                .as(this::ok);
    }

    private Mono<Payment> lookup(PaymentProvider provider, String orderId, String orderNumber) {
        if (orderId != null && !orderId.isBlank()) {
            return paymentRepository.findByProviderOrderId(provider, orderId);
        }
        if (orderNumber != null && !orderNumber.isBlank()) {
            return paymentRepository.findByOrderNumber(orderNumber);
        }
        return Mono.empty();
    }
}
