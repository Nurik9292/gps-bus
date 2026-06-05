package biz.ugur.busroutebackend.interfaces.rest.client.V1.controller;

import biz.ugur.busroutebackend.client.infrastructure.security.ClientPrincipal;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import biz.ugur.busroutebackend.subscription.application.dto.InitiateSubscriptionPaymentCommand;
import biz.ugur.busroutebackend.subscription.application.dto.InitiateSubscriptionPaymentResponse;
import biz.ugur.busroutebackend.subscription.application.dto.SubscriptionResponse;
import biz.ugur.busroutebackend.subscription.application.usecase.client.GetCurrentClientSubscriptionUseCase;
import biz.ugur.busroutebackend.subscription.application.usecase.client.InitiateClientSubscriptionPaymentUseCase;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_CLIENT_SUBSCRIPTIONS;

@RestController
@RequestMapping(V1_CLIENT_SUBSCRIPTIONS)
public class ClientSubscriptionController extends BaseController {

    private final InitiateClientSubscriptionPaymentUseCase initiateUseCase;
    private final GetCurrentClientSubscriptionUseCase getCurrentUseCase;

    public ClientSubscriptionController(InitiateClientSubscriptionPaymentUseCase initiateUseCase,
                                         GetCurrentClientSubscriptionUseCase getCurrentUseCase,
                                         MessageSource messageSource) {
        super(messageSource);
        this.initiateUseCase = initiateUseCase;
        this.getCurrentUseCase = getCurrentUseCase;
    }

    @Override
    protected String getControllerName() {
        return ClientSubscriptionController.class.getSimpleName();
    }

    @PostMapping("/payment")
    public Mono<ResponseEntity<ApiResponse<InitiateSubscriptionPaymentResponse>>> initiatePayment(
            @Valid @RequestBody PaymentRequest request) {
        return created(getCurrentPrincipal()
                .flatMap(principal -> initiateUseCase.execute(
                        new InitiateSubscriptionPaymentCommand(
                                principal.getClientId(),
                                request.bank(),
                                request.period()))));
    }

    @GetMapping("/current")
    public Mono<ResponseEntity<ApiResponse<SubscriptionResponse>>> getCurrent() {
        return ok(getCurrentPrincipal()
                .flatMap(principal -> getCurrentUseCase.execute(principal.getClientId())));
    }

    private Mono<ClientPrincipal> getCurrentPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(auth -> (ClientPrincipal) auth.getPrincipal());
    }

    public record PaymentRequest(String bank, String period) {
        public PaymentRequest {
            bank = bank == null ? null : bank.trim().toUpperCase();
            period = period == null ? null : period.trim().toUpperCase();
        }
    }
}
