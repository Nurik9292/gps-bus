package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.application.dto.AdTariffResponse;
import biz.ugur.busroutebackend.advertising.application.dto.CreateAdTariffCommand;
import biz.ugur.busroutebackend.advertising.application.factory.AdTariffFactory;
import biz.ugur.busroutebackend.advertising.application.mapper.AdTariffResponseMapper;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.advertising.domain.enums.TariffPeriod;
import biz.ugur.busroutebackend.advertising.domain.model.AdTariff;
import biz.ugur.busroutebackend.advertising.domain.repository.AdTariffRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.Price;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.TariffName;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class CreateAdTariffUseCaseTest {

    @InjectMocks
    private CreateAdTariffUseCase useCase;

    @Mock
    private AdTariffRepository tariffRepository;

    @Mock
    private AdTariffFactory tariffFactory;

    @Mock
    private AdTariffResponseMapper responseMapper;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void createsAndPersistsTariff() {
        CreateAdTariffCommand cmd = new CreateAdTariffCommand(
                "Premium", "desc", "BANNER", "WEEK",
                100_00L, "TMT", 1000, 100, 50, 0);

        AdTariff tariff = AdTariff.create(
                TariffName.of("Premium"), "desc",
                PlacementType.BANNER, TariffPeriod.WEEK,
                Price.ofMinor(100_00L, "TMT"),
                1000, 100, 50, 0);

        AdTariffResponse response = mock(AdTariffResponse.class);
        when(response.id()).thenReturn(tariff.getId().getValue());
        when(response.name()).thenReturn("Premium");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(tariffFactory.create(cmd)).thenReturn(Mono.just(tariff));
        when(tariffRepository.save(tariff)).thenReturn(Mono.just(tariff));
        when(responseMapper.toResponse(tariff)).thenReturn(Mono.just(response));

        StepVerifier.create(useCase.execute(Mono.just(cmd)))
                .assertNext(r -> assertEquals("Premium", r.name()))
                .verifyComplete();

        verify(tariffRepository).save(tariff);
    }

    @Test
    void exposesBoundContext() {
        assertEquals("advertising.admin", useCase.getBoundContext());
    }
}
