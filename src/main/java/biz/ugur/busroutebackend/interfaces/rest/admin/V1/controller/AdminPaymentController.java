package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.payment.application.dto.InitiatePaymentCommand;
import biz.ugur.busroutebackend.payment.application.dto.InitiatePaymentResponse;
import biz.ugur.busroutebackend.payment.application.dto.PaymentList;
import biz.ugur.busroutebackend.payment.application.dto.PaymentResponse;
import biz.ugur.busroutebackend.payment.application.usecase.admin.GetPaymentByIdUseCase;
import biz.ugur.busroutebackend.payment.application.usecase.admin.GetPaymentsPaginatedUseCase;
import biz.ugur.busroutebackend.payment.application.usecase.admin.InitiatePaymentUseCase;
import biz.ugur.busroutebackend.payment.application.usecase.admin.RefundPaymentUseCase;
import biz.ugur.busroutebackend.payment.application.usecase.admin.SyncPaymentStatusUseCase;
import biz.ugur.busroutebackend.shared.infrastructure.web.BasePaginatedController;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_PAYMENTS;

@RestController
@RequestMapping(V1_ADMIN_PAYMENTS)
@CrossOrigin(origins = "*")
public class AdminPaymentController extends BasePaginatedController {

    private final InitiatePaymentUseCase initiatePaymentUseCase;
    private final GetPaymentByIdUseCase getPaymentByIdUseCase;
    private final GetPaymentsPaginatedUseCase getPaymentsPaginatedUseCase;
    private final SyncPaymentStatusUseCase syncPaymentStatusUseCase;
    private final RefundPaymentUseCase refundPaymentUseCase;

    public AdminPaymentController(InitiatePaymentUseCase initiatePaymentUseCase,
                                   GetPaymentByIdUseCase getPaymentByIdUseCase,
                                   GetPaymentsPaginatedUseCase getPaymentsPaginatedUseCase,
                                   SyncPaymentStatusUseCase syncPaymentStatusUseCase,
                                   RefundPaymentUseCase refundPaymentUseCase,
                                   MessageSource messageSource) {
        super(messageSource);
        this.initiatePaymentUseCase = initiatePaymentUseCase;
        this.getPaymentByIdUseCase = getPaymentByIdUseCase;
        this.getPaymentsPaginatedUseCase = getPaymentsPaginatedUseCase;
        this.syncPaymentStatusUseCase = syncPaymentStatusUseCase;
        this.refundPaymentUseCase = refundPaymentUseCase;
    }

    @Override
    protected String getControllerName() {
        return AdminPaymentController.class.getSimpleName();
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<PaymentList>>> list(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        validatePagination(page, size);
        return okPaginated(getPaymentsPaginatedUseCase.execute(
                new GetPaymentsPaginatedUseCase.Query(page, size, status)));
    }

    @PostMapping
    public Mono<ResponseEntity<ApiResponse<InitiatePaymentResponse>>> initiate(
            @Valid @RequestBody InitiatePaymentCommand request) {
        return created(initiatePaymentUseCase.execute(Mono.just(request)));
    }

    @GetMapping("/{paymentId}")
    public Mono<ResponseEntity<ApiResponse<PaymentResponse>>> getById(@PathVariable String paymentId) {
        return ok(getPaymentByIdUseCase.execute(paymentId));
    }

    @PostMapping("/{paymentId}/sync")
    public Mono<ResponseEntity<ApiResponse<PaymentResponse>>> sync(@PathVariable String paymentId) {
        return ok(syncPaymentStatusUseCase.execute(paymentId));
    }

    @PostMapping("/{paymentId}/refund")
    public Mono<ResponseEntity<ApiResponse<PaymentResponse>>> refund(@PathVariable String paymentId) {
        return ok(refundPaymentUseCase.execute(paymentId));
    }
}
