package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.application.dto.CreateAdPlacementCommand;
import biz.ugur.busroutebackend.advertising.application.dto.CreateAdPlacementResponse;
import biz.ugur.busroutebackend.advertising.application.dto.PaymentMethod;
import biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig;
import biz.ugur.busroutebackend.advertising.application.factory.AdPlacementFactory;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementKind;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementTargetRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdTariffRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffId;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentSubjectType;
import biz.ugur.busroutebackend.payment.domain.model.Payment;
import biz.ugur.busroutebackend.payment.domain.repository.PaymentRepository;
import biz.ugur.busroutebackend.payment.domain.services.PaymentOrchestrator;
import biz.ugur.busroutebackend.payment.domain.services.PaymentProviderGateway;
import biz.ugur.busroutebackend.payment.domain.valueobjects.Money;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.SecurityContextService;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
public class CreateAdPlacementUseCase
        extends BaseUseCase<Mono<CreateAdPlacementCommand>, CreateAdPlacementResponse> {

    private static final Duration BANK_PAYMENT_EXPIRY = Duration.ofMinutes(30);
    private static final Duration CASH_PAYMENT_EXPIRY = Duration.ofDays(7);
    private static final String CASH_RETURN_URL = "cash://no-redirect";

    private final AdPlacementRepository placementRepository;
    private final AdPlacementTargetRepository targetRepository;
    private final AdPlacementFactory placementFactory;
    private final AdTariffRepository tariffRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentOrchestrator paymentOrchestrator;
    private final SecurityContextService securityService;

    public CreateAdPlacementUseCase(AdPlacementRepository placementRepository,
                                     AdPlacementTargetRepository targetRepository,
                                     AdPlacementFactory placementFactory,
                                     AdTariffRepository tariffRepository,
                                     PaymentRepository paymentRepository,
                                     PaymentOrchestrator paymentOrchestrator,
                                     SecurityContextService securityService,
                                     CorrelationContextService correlationService,
                                     EventBus eventBus) {
        super(correlationService, eventBus);
        this.placementRepository = placementRepository;
        this.targetRepository = targetRepository;
        this.placementFactory = placementFactory;
        this.tariffRepository = tariffRepository;
        this.paymentRepository = paymentRepository;
        this.paymentOrchestrator = paymentOrchestrator;
        this.securityService = securityService;
    }

    @Override
    protected Mono<CreateAdPlacementResponse> process(Mono<CreateAdPlacementCommand> request) {
        return request.flatMap(cmd -> {
            cmd.validatePaymentConsistency();
            cmd.validateContentConsistency();
            return securityService.getCurrentUsername()
                    .flatMap(username -> placementFactory.create(cmd)
                            .flatMap(CreateAdPlacementUseCase::requireTargets)
                            .map(placement -> placement.approve(username))
                            .map(CreateAdPlacementUseCase::hotFixEditorialStatus)
                            .flatMap(placement -> placementRepository.save(placement)
                                    .flatMap(saved -> targetRepository.replaceAll(
                                                    saved.getId(), placement.getTargets())
                                            .then(createPaymentIfCommercial(saved, cmd)
                                                    .map(Optional::of)
                                                    .defaultIfEmpty(Optional.empty()))
                                            .map(paymentOpt -> CreateAdPlacementResponse.of(
                                                    saved.withTargets(placement.getTargets()),
                                                    paymentOpt.orElse(null)))))
                            .doOnSuccess(r -> log.info("AdPlacement created and auto-approved: id={}",
                                    r.placement().id())));
        });
    }

    private static Mono<AdPlacement> requireTargets(AdPlacement placement) {
        if (placement.getTargets() == null || placement.getTargets().isEmpty()) {
            return Mono.error(new AdvertisingValidationException(
                    "targets", "at least one placement target is required"));
        }
        return Mono.just(placement);
    }

    static AdPlacement hotFixEditorialStatus(AdPlacement p) {
        if (p.getKind() != PlacementKind.EDITORIAL
                || p.getStatus() != PlacementStatus.PENDING_PAYMENT) {
            return p;
        }
        AdPlacement scheduled = p.markAsScheduled();
        LocalDateTime startsAt = scheduled.getWindow() != null ? scheduled.getWindow().getStartsAt() : null;
        if (startsAt == null || !startsAt.isAfter(LocalDateTime.now())) {
            return scheduled.markAsActive();
        }
        return scheduled;
    }

    private Mono<Payment> createPaymentIfCommercial(AdPlacement placement, CreateAdPlacementCommand cmd) {
        if (placement.getKind() != PlacementKind.COMMERCIAL) {
            return Mono.empty();
        }
        PaymentProvider provider = resolveProvider(cmd);
        return tariffRepository.findById(TariffId.of(cmd.tariffId()))
                .switchIfEmpty(Mono.defer(() -> Mono.error(
                        new IllegalStateException("Tariff not found: " + cmd.tariffId()))))
                .flatMap(tariff -> {
                    Money money = Money.ofMinor(tariff.getPrice().getAmountMinor(),
                            tariff.getPrice().getCurrency());
                    String returnUrl = provider == PaymentProvider.CASH
                            ? CASH_RETURN_URL
                            : buildReturnUrl(placement);
                    Duration expiry = provider == PaymentProvider.CASH
                            ? CASH_PAYMENT_EXPIRY
                            : BANK_PAYMENT_EXPIRY;
                    Payment draft = Payment.register(
                            provider,
                            PaymentSubjectType.AD_PLACEMENT,
                            placement.getId().getValue(),
                            placement.getBusinessId().getValue(),
                            money,
                            returnUrl,
                            LocalDateTime.now().plus(expiry));
                    return paymentRepository.save(draft);
                })
                .flatMap(saved -> paymentOrchestrator.register(saved)
                        .flatMap(result -> attachOrderIfPresent(saved, result)));
    }

    private Mono<Payment> attachOrderIfPresent(Payment payment,
                                                PaymentProviderGateway.RegisterResult result) {
        if (result.providerOrderId() == null || result.providerOrderId().isBlank()) {
            return Mono.just(payment);
        }
        return paymentRepository.save(payment.attachProviderOrder(
                result.providerOrderId(), result.formUrl()));
    }

    private PaymentProvider resolveProvider(CreateAdPlacementCommand cmd) {
        return cmd.paymentMethod() == PaymentMethod.CASH
                ? PaymentProvider.CASH
                : PaymentProvider.from(cmd.paymentProvider());
    }

    private String buildReturnUrl(AdPlacement placement) {
        return ApiVersionConfig.V1_ADMIN_AD_PLACEMENT_PAYMENT_CALLBACK
                .replace("{placementId}", placement.getId().getValue());
    }

    @Override
    protected String getBoundContext() {
        return "advertising.admin";
    }
}
