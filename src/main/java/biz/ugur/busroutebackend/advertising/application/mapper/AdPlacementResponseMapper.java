package biz.ugur.busroutebackend.advertising.application.mapper;

import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementResponse;
import biz.ugur.busroutebackend.advertising.application.dto.PaymentInfo;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementTargetRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentStatus;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentSubjectType;
import biz.ugur.busroutebackend.payment.domain.repository.PaymentRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Component
public class AdPlacementResponseMapper {

    private final AdPlacementTargetRepository targetRepository;
    private final PaymentRepository paymentRepository;

    public AdPlacementResponseMapper(AdPlacementTargetRepository targetRepository,
                                     PaymentRepository paymentRepository) {
        this.targetRepository = targetRepository;
        this.paymentRepository = paymentRepository;
    }

    public Mono<AdPlacementResponse> toResponse(AdPlacement placement) {
        Mono<AdPlacement> enrichedWithTargets = placement.getTargets() != null && !placement.getTargets().isEmpty()
                ? Mono.just(placement)
                : targetRepository.findByPlacementId(placement.getId())
                        .collectList()
                        .map(placement::withTargets);

        Mono<Optional<PaymentInfo>> pendingCashPayment = paymentRepository.findFirstBySubjectAndProviderAndStatus(
                        PaymentSubjectType.AD_PLACEMENT,
                        placement.getId().getValue(),
                        PaymentProvider.CASH,
                        PaymentStatus.REGISTERED)
                .map(p -> Optional.of(PaymentInfo.fromDomain(p)))
                .defaultIfEmpty(Optional.empty());

        return Mono.zip(enrichedWithTargets, pendingCashPayment)
                .map(t -> buildResponse(t.getT1(), t.getT2().orElse(null)));
    }

    public Flux<AdPlacementResponse> toResponses(List<AdPlacement> placements) {
        if (placements == null || placements.isEmpty()) {
            return Flux.empty();
        }
        List<PlacementId> ids = placements.stream().map(AdPlacement::getId).toList();
        return targetRepository.findByPlacementIds(ids)
                .flatMapMany(targetMap -> Flux.fromIterable(placements)
                        .map(p -> {
                            AdPlacement enriched = p.withTargets(
                                    targetMap.getOrDefault(p.getId().getValue(), List.of()));
                            return buildResponse(enriched, null);
                        }));
    }

    private AdPlacementResponse buildResponse(AdPlacement placement, PaymentInfo pendingCashPayment) {
        AdPlacementResponse base = AdPlacementResponse.fromDomain(placement);
        return new AdPlacementResponse(
                base.id(),
                base.businessId(),
                base.tariffId(),
                base.placementType(),
                base.kind(),
                base.status(),
                base.title(),
                base.content(),
                base.contentType(),
                base.imageUrl(),
                base.targetUrl(),
                base.ctaText(),
                base.startsAt(),
                base.endsAt(),
                base.targets(),
                base.displayOrder(),
                base.rejectionReason(),
                base.approvedAt(),
                base.approvedByAdminId(),
                base.rejectedAt(),
                base.rejectedByAdminId(),
                base.createdAt(),
                base.updatedAt(),
                pendingCashPayment
        );
    }
}
