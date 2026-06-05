package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.payment.application.dto.PaymentList;
import biz.ugur.busroutebackend.shared.infrastructure.web.BasePaginatedController;
import biz.ugur.busroutebackend.subscription.application.dto.SubscriptionList;
import biz.ugur.busroutebackend.subscription.application.dto.SubscriptionResponse;
import biz.ugur.busroutebackend.subscription.application.usecase.admin.GetClientTransactionsUseCase;
import biz.ugur.busroutebackend.subscription.application.usecase.admin.GetSubscriptionByIdUseCase;
import biz.ugur.busroutebackend.subscription.application.usecase.admin.GetSubscriptionsPaginatedUseCase;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_SUBSCRIPTIONS;

@RestController
@RequestMapping(V1_ADMIN_SUBSCRIPTIONS)
public class AdminSubscriptionController extends BasePaginatedController {

    private final GetSubscriptionsPaginatedUseCase getSubscriptionsPaginatedUseCase;
    private final GetSubscriptionByIdUseCase getSubscriptionByIdUseCase;
    private final GetClientTransactionsUseCase getClientTransactionsUseCase;

    public AdminSubscriptionController(GetSubscriptionsPaginatedUseCase getSubscriptionsPaginatedUseCase,
                                       GetSubscriptionByIdUseCase getSubscriptionByIdUseCase,
                                       GetClientTransactionsUseCase getClientTransactionsUseCase,
                                       MessageSource messageSource) {
        super(messageSource);
        this.getSubscriptionsPaginatedUseCase = getSubscriptionsPaginatedUseCase;
        this.getSubscriptionByIdUseCase = getSubscriptionByIdUseCase;
        this.getClientTransactionsUseCase = getClientTransactionsUseCase;
    }

    @Override
    protected String getControllerName() {
        return AdminSubscriptionController.class.getSimpleName();
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<SubscriptionList>>> list(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String period) {
        validatePagination(page, size);
        return okPaginated(getSubscriptionsPaginatedUseCase.execute(
                new GetSubscriptionsPaginatedUseCase.Query(page, size, status, period)));
    }

    @GetMapping("/{subscriptionId}")
    public Mono<ResponseEntity<ApiResponse<SubscriptionResponse>>> getById(@PathVariable String subscriptionId) {
        return ok(getSubscriptionByIdUseCase.execute(subscriptionId));
    }

    @GetMapping("/by-client/{clientId}/transactions")
    public Mono<ResponseEntity<ApiResponse<PaymentList>>> clientTransactions(
            @PathVariable String clientId,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        validatePagination(page, size);
        return okPaginated(getClientTransactionsUseCase.execute(
                new GetClientTransactionsUseCase.Query(clientId, page, size, status, from, to)));
    }
}
