package biz.ugur.busroutebackend.subscription.application.usecase.admin;

import biz.ugur.busroutebackend.client.application.service.ClientLookupService;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.subscription.application.dto.SubscriptionList;
import biz.ugur.busroutebackend.subscription.application.dto.SubscriptionResponse;
import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionPeriod;
import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionStatus;
import biz.ugur.busroutebackend.subscription.domain.model.Subscription;
import biz.ugur.busroutebackend.subscription.domain.repository.SubscriptionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class GetSubscriptionsPaginatedUseCase
        extends BaseUseCase<GetSubscriptionsPaginatedUseCase.Query, SubscriptionList> {

    public record Query(int page, int size, String status, String period) {}

    private final SubscriptionRepository subscriptionRepository;
    private final ClientLookupService clientLookupService;

    public GetSubscriptionsPaginatedUseCase(SubscriptionRepository subscriptionRepository,
                                            ClientLookupService clientLookupService,
                                            CorrelationContextService correlationService,
                                            EventBus eventBus) {
        super(correlationService, eventBus);
        this.subscriptionRepository = subscriptionRepository;
        this.clientLookupService = clientLookupService;
    }

    @Override
    protected Mono<SubscriptionList> process(Query q) {
        SubscriptionStatus status = parseStatus(q.status());
        SubscriptionPeriod period = parsePeriod(q.period());
        var pageable = PageRequest.of(q.page() - 1, q.size());

        var items = subscriptionRepository.findPaginated(status, period, pageable)
                .collectList()
                .flatMap(this::attachClients);
        var total = subscriptionRepository.countFiltered(status, period);
        var activeCount = subscriptionRepository.countFiltered(SubscriptionStatus.ACTIVE, null);

        return items.zipWith(total)
                .zipWith(activeCount)
                .map(tuple -> SubscriptionList.of(
                        tuple.getT1().getT1(),
                        tuple.getT2(),
                        q.page(), q.size(),
                        tuple.getT1().getT2()));
    }

    private Mono<List<SubscriptionResponse>> attachClients(List<Subscription> subscriptions) {
        List<String> clientIds = subscriptions.stream()
                .map(Subscription::getClientId)
                .distinct()
                .toList();
        return clientLookupService.findByIds(clientIds)
                .map(clientsById -> subscriptions.stream()
                        .map(s -> SubscriptionResponse.fromDomain(s).withClient(clientsById.get(s.getClientId())))
                        .toList());
    }

    private SubscriptionStatus parseStatus(String raw) {
        return (raw == null || raw.isBlank()) ? null : SubscriptionStatus.valueOf(raw.trim().toUpperCase());
    }

    private SubscriptionPeriod parsePeriod(String raw) {
        return (raw == null || raw.isBlank()) ? null : SubscriptionPeriod.from(raw);
    }

    @Override
    protected String getBoundContext() { return "subscription.admin"; }
}
