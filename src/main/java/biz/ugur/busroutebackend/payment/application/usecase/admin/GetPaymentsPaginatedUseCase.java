package biz.ugur.busroutebackend.payment.application.usecase.admin;

import biz.ugur.busroutebackend.payment.application.dto.PaymentList;
import biz.ugur.busroutebackend.payment.application.dto.PaymentResponse;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentStatus;
import biz.ugur.busroutebackend.payment.domain.repository.PaymentRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class GetPaymentsPaginatedUseCase
        extends BaseUseCase<GetPaymentsPaginatedUseCase.Query, PaymentList> {

    public record Query(int page, int size, String status) {}

    private final PaymentRepository paymentRepository;

    public GetPaymentsPaginatedUseCase(PaymentRepository paymentRepository,
                                        CorrelationContextService correlationService,
                                        EventBus eventBus) {
        super(correlationService, eventBus);
        this.paymentRepository = paymentRepository;
    }

    @Override
    protected Mono<PaymentList> process(Query q) {
        var pageable = PageRequest.of(q.page() - 1, q.size(),
                Sort.by(Sort.Direction.DESC, "initiated_at"));

        Flux<biz.ugur.busroutebackend.payment.domain.model.Payment> items;
        Mono<Long> total;

        if (q.status() != null && !q.status().isBlank()) {
            PaymentStatus status = PaymentStatus.valueOf(q.status().toUpperCase());
            items = paymentRepository.findByStatus(status, pageable);
            total = paymentRepository.countByStatus(status);
        } else {
            items = paymentRepository.findAll(pageable);
            total = paymentRepository.count();
        }

        Mono<Long> completedCount = paymentRepository.countByStatus(PaymentStatus.COMPLETED);

        return items
                .map(PaymentResponse::fromDomain)
                .collectList()
                .zipWith(total)
                .zipWith(completedCount)
                .map(tuple -> PaymentList.of(
                        tuple.getT1().getT1(),
                        tuple.getT2(),
                        q.page(), q.size(),
                        tuple.getT1().getT2()));
    }

    @Override
    protected String getBoundContext() { return "payment.admin"; }
}
