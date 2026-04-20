package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.application.dto.AdTariffResponse;
import biz.ugur.busroutebackend.advertising.application.dto.UpdateAdTariffCommand;
import biz.ugur.busroutebackend.advertising.application.mapper.AdTariffResponseMapper;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.enums.TariffPeriod;
import biz.ugur.busroutebackend.advertising.domain.exceptions.AdTariffNotFoundException;
import biz.ugur.busroutebackend.advertising.domain.model.AdTariff;
import biz.ugur.busroutebackend.advertising.domain.repository.AdTariffRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.Price;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffId;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffName;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class UpdateAdTariffUseCaseTest {

    @InjectMocks
    private UpdateAdTariffUseCase useCase;

    @Mock
    private AdTariffRepository tariffRepository;

    @Mock
    private AdTariffResponseMapper responseMapper;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    private AdTariff tariff;

    @BeforeEach
    void setUp() {
        tariff = AdTariff.create(
                TariffName.of("Basic"), "old",
                PlacementType.BANNER, TariffPeriod.WEEK,
                Price.ofMinor(100L, "TMT"),
                10, 1, 1, 0);
    }

    @Test
    void updatesTariffAndReturnsResponse() {
        UpdateAdTariffCommand cmd = new UpdateAdTariffCommand(
                "Premium", "new-desc", 500L, null, 100, 10, 5, 2);
        AdTariffResponse response = mock(AdTariffResponse.class);
        when(response.id()).thenReturn(tariff.getId().getValue());

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(tariffRepository.findById(tariff.getId())).thenReturn(Mono.just(tariff));
        when(tariffRepository.save(any(AdTariff.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(responseMapper.toResponse(any(AdTariff.class))).thenReturn(Mono.just(response));

        StepVerifier.create(useCase.execute(new UpdateAdTariffUseCase.Request(
                tariff.getId().getValue(), cmd)))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<AdTariff> captor = ArgumentCaptor.forClass(AdTariff.class);
        verify(tariffRepository).save(captor.capture());
        assertEquals("Premium", captor.getValue().getName().getValue());
    }

    @Test
    void errorsWhenTariffNotFound() {
        UpdateAdTariffCommand cmd = new UpdateAdTariffCommand(
                null, null, null, null, null, null, null, null);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(tariffRepository.findById(any(TariffId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(new UpdateAdTariffUseCase.Request(
                TariffId.generate().getValue(), cmd)))
                .expectErrorSatisfies(err -> assertInstanceOf(AdTariffNotFoundException.class, err))
                .verify();
    }

    @Test
    void exposesBoundContext() {
        assertEquals("advertising.admin", useCase.getBoundContext());
    }
}
