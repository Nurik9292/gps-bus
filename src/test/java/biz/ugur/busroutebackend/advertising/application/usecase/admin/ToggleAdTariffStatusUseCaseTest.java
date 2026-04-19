package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.application.dto.AdTariffResponse;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class ToggleAdTariffStatusUseCaseTest {

    @InjectMocks
    private ToggleAdTariffStatusUseCase useCase;

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
                TariffName.of("Premium"), "desc",
                PlacementType.BANNER, TariffPeriod.WEEK,
                Price.ofMinor(100_00L, "TMT"),
                1000, 100, 50, 0
        );
    }

    @Test
    void activatesTariffWhenRequestActivateIsTrue() {
        AdTariff inactive = tariff.deactivate();
        AdTariffResponse response = stubResponse(tariff.getId().getValue(), true);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(tariffRepository.findById(tariff.getId())).thenReturn(Mono.just(inactive));
        when(tariffRepository.save(any(AdTariff.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(responseMapper.toResponse(any(AdTariff.class))).thenReturn(Mono.just(response));

        StepVerifier.create(useCase.execute(
                new ToggleAdTariffStatusUseCase.Request(tariff.getId().getValue(), true)))
                .assertNext(r -> assertTrue(r.isActive()))
                .verifyComplete();
    }

    @Test
    void deactivatesTariffWhenRequestActivateIsFalse() {
        AdTariffResponse response = stubResponse(tariff.getId().getValue(), false);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(tariffRepository.findById(tariff.getId())).thenReturn(Mono.just(tariff));
        when(tariffRepository.save(any(AdTariff.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(responseMapper.toResponse(any(AdTariff.class))).thenReturn(Mono.just(response));

        StepVerifier.create(useCase.execute(
                new ToggleAdTariffStatusUseCase.Request(tariff.getId().getValue(), false)))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void errorsWhenTariffNotFound() {
        String id = TariffId.generate().getValue();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(tariffRepository.findById(any(TariffId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(new ToggleAdTariffStatusUseCase.Request(id, true)))
                .expectErrorSatisfies(err -> assertInstanceOf(AdTariffNotFoundException.class, err))
                .verify();
    }

    @Test
    void exposesBoundContext() {
        assertEquals("advertising.admin", useCase.getBoundContext());
    }

    private AdTariffResponse stubResponse(String id, boolean active) {
        AdTariffResponse response = org.mockito.Mockito.mock(AdTariffResponse.class);
        when(response.isActive()).thenReturn(active);
        return response;
    }
}
