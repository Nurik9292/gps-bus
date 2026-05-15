package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.application.dto.CreateAdPlacementCommand;
import biz.ugur.busroutebackend.advertising.application.dto.PaymentMethod;
import biz.ugur.busroutebackend.advertising.application.factory.AdPlacementFactory;
import biz.ugur.busroutebackend.advertising.domain.enums.ContentType;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementKind;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.model.AdTariff;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementTargetRepository;
import biz.ugur.busroutebackend.advertising.domain.repository.AdTariffRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementTarget;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.Price;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffId;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
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
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
class CreateAdPlacementUseCaseTest {

    private CreateAdPlacementUseCase useCase;

    @Mock private AdPlacementRepository placementRepository;
    @Mock private AdPlacementTargetRepository targetRepository;
    @Mock private AdPlacementFactory placementFactory;
    @Mock private AdTariffRepository tariffRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentOrchestrator paymentOrchestrator;
    @Mock private SecurityContextService securityService;
    @Mock private CorrelationContextService correlationService;
    @Mock private EventBus eventBus;

    @BeforeEach
    void setUp() {
        useCase = new CreateAdPlacementUseCase(
                placementRepository, targetRepository, placementFactory,
                tariffRepository, paymentRepository, paymentOrchestrator,
                securityService, correlationService, eventBus);

        when(correlationService.getCurrentCorrelationId())
                .thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(securityService.getCurrentUsername()).thenReturn(Mono.just("admin1"));
    }

    @Test
    void process_editorial_autoApprovesAndReturnsNoPayment() {
        AdPlacement draft = buildDraftPlacement(PlacementKind.EDITORIAL);
        AdPlacement approved = draft.approve("admin1");
        AdPlacement scheduled = approved.markAsScheduled();
        CreateAdPlacementCommand cmd = editorialCommand(draft);

        when(placementFactory.create(cmd)).thenReturn(Mono.just(draft));
        when(placementRepository.save(any(AdPlacement.class))).thenReturn(Mono.just(scheduled));
        when(targetRepository.replaceAll(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .assertNext(r -> {
                    assertNotNull(r.placement());
                    assertNull(r.payment());
                })
                .verifyComplete();

        verify(paymentRepository, never()).save(any());
        verify(placementRepository).save(argThat(p -> p.getStatus() == PlacementStatus.SCHEDULED));
    }

    @Test
    void process_commercialWithCash_autoApprovesAndCreatesCashPayment() {
        AdPlacement draft = buildDraftPlacement(PlacementKind.COMMERCIAL);
        AdPlacement approved = draft.approve("admin1");
        AdTariff tariff = buildTariff(draft.getTariffId(), 50000L, "TMT");
        Payment savedPayment = buildPayment(PaymentProvider.CASH, approved.getId().getValue(),
                approved.getBusinessId().getValue());

        CreateAdPlacementCommand cmd = commercialCommand(draft, PaymentMethod.CASH, null);

        when(placementFactory.create(cmd)).thenReturn(Mono.just(draft));
        when(placementRepository.save(any(AdPlacement.class))).thenReturn(Mono.just(approved));
        when(targetRepository.replaceAll(any(), any())).thenReturn(Mono.empty());
        when(tariffRepository.findById(any(TariffId.class))).thenReturn(Mono.just(tariff));
        when(paymentRepository.save(any(Payment.class))).thenReturn(Mono.just(savedPayment));
        when(paymentOrchestrator.register(any(Payment.class)))
                .thenReturn(Mono.just(new PaymentProviderGateway.RegisterResult(null, null)));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .assertNext(r -> {
                    assertNotNull(r.placement());
                    assertNotNull(r.payment());
                    assertEquals("CASH", r.payment().provider());
                    assertNull(r.payment().formUrl());
                })
                .verifyComplete();

        verify(placementRepository).save(argThat(p -> p.getStatus() == PlacementStatus.PENDING_PAYMENT));
        verify(paymentRepository, times(1)).save(any(Payment.class));
    }

    @Test
    void process_commercialWithBank_createsBankPaymentWithFormUrl() {
        AdPlacement draft = buildDraftPlacement(PlacementKind.COMMERCIAL);
        AdPlacement approved = draft.approve("admin1");
        AdTariff tariff = buildTariff(draft.getTariffId(), 75000L, "TMT");
        Payment savedPayment = buildPayment(PaymentProvider.RYSGAL, approved.getId().getValue(),
                approved.getBusinessId().getValue());
        Payment savedWithOrder = savedPayment.attachProviderOrder("order-123", "https://pay.example.com");

        CreateAdPlacementCommand cmd = commercialCommand(draft, PaymentMethod.BANK, "RYSGAL");

        when(placementFactory.create(cmd)).thenReturn(Mono.just(draft));
        when(placementRepository.save(any(AdPlacement.class))).thenReturn(Mono.just(approved));
        when(targetRepository.replaceAll(any(), any())).thenReturn(Mono.empty());
        when(tariffRepository.findById(any(TariffId.class))).thenReturn(Mono.just(tariff));
        when(paymentRepository.save(any(Payment.class)))
                .thenReturn(Mono.just(savedPayment))
                .thenReturn(Mono.just(savedWithOrder));
        when(paymentOrchestrator.register(any(Payment.class)))
                .thenReturn(Mono.just(new PaymentProviderGateway.RegisterResult("order-123", "https://pay.example.com")));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .assertNext(r -> {
                    assertNotNull(r.payment());
                    assertEquals("https://pay.example.com", r.payment().formUrl());
                })
                .verifyComplete();

        verify(paymentRepository, times(2)).save(any(Payment.class));
    }

    @Test
    void process_commercialTariffNotFound_propagatesError() {
        AdPlacement draft = buildDraftPlacement(PlacementKind.COMMERCIAL);
        AdPlacement approved = draft.approve("admin1");
        CreateAdPlacementCommand cmd = commercialCommand(draft, PaymentMethod.CASH, null);

        when(placementFactory.create(cmd)).thenReturn(Mono.just(draft));
        when(placementRepository.save(any(AdPlacement.class))).thenReturn(Mono.just(approved));
        when(targetRepository.replaceAll(any(), any())).thenReturn(Mono.empty());
        when(tariffRepository.findById(any(TariffId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectErrorSatisfies(err -> {
                    assertInstanceOf(IllegalStateException.class, err);
                    assertTrue(err.getMessage().contains("Tariff not found"));
                })
                .verify();
    }

    @Test
    void process_commercialWithNullPaymentMethod_validationFails() {
        AdPlacement draft = buildDraftPlacement(PlacementKind.COMMERCIAL);
        CreateAdPlacementCommand cmd = commercialCommand(draft, null, null);

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectErrorSatisfies(err -> assertInstanceOf(IllegalArgumentException.class, err))
                .verify();
    }

    @Test
    void process_factoryValidationError_propagates() {
        CreateAdPlacementCommand cmd = editorialCommand(buildDraftPlacement(PlacementKind.EDITORIAL));
        when(placementFactory.create(cmd))
                .thenReturn(Mono.error(new AdvertisingValidationException(
                        "targetId", "must not be blank for targetType ROUTE")));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .expectErrorSatisfies(err ->
                        assertInstanceOf(AdvertisingValidationException.class, err))
                .verify();
    }

    @Test
    void exposesBoundContext() {
        assertEquals("advertising.admin", useCase.getBoundContext());
    }

    private AdPlacement buildDraftPlacement(PlacementKind kind) {
        return AdPlacement.create(
                BusinessId.generate(), TariffId.generate(), PlacementType.BANNER,
                kind, "Test Ad", null, null, "https://target", null,
                ContentType.LINK, null,
                List.of(PlacementTarget.general(TargetType.HOME)), 0);
    }

    private AdTariff buildTariff(TariffId id, long amountMinor, String currency) {
        AdTariff tariff = mock(AdTariff.class);
        when(tariff.getId()).thenReturn(id);
        Price price = Price.ofMinor(amountMinor, currency);
        when(tariff.getPrice()).thenReturn(price);
        return tariff;
    }

    private Payment buildPayment(PaymentProvider provider, String subjectId, String businessId) {
        String returnUrl = provider == PaymentProvider.CASH
                ? "cash://no-redirect"
                : "https://return.example.com";
        return Payment.register(
                provider,
                PaymentSubjectType.AD_PLACEMENT,
                subjectId,
                businessId,
                Money.ofMinor(50000L, "TMT"),
                returnUrl,
                LocalDateTime.now().plusMinutes(30));
    }

    private CreateAdPlacementCommand editorialCommand(AdPlacement placement) {
        return new CreateAdPlacementCommand(
                null, null, "BANNER", "EDITORIAL",
                "Editorial Title", "content", null, null, null,
                null, null, List.of(), 0, null, null, ContentType.CONTENT);
    }

    private CreateAdPlacementCommand commercialCommand(AdPlacement placement,
                                                        PaymentMethod paymentMethod,
                                                        String paymentProvider) {
        String tariffId = placement != null ? placement.getTariffId().getValue() : TariffId.generate().getValue();
        String businessId = placement != null ? placement.getBusinessId().getValue() : BusinessId.generate().getValue();
        return new CreateAdPlacementCommand(
                businessId, tariffId, "BANNER", "COMMERCIAL",
                "Commercial Ad", null, null, "https://target", null,
                null, null, List.of(), 0, paymentMethod, paymentProvider, ContentType.LINK);
    }
}
