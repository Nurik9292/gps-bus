package biz.ugur.busroutebackend.advertising.application.usecase.admin;

import biz.ugur.busroutebackend.advertising.application.dto.AdTariffResponse;
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
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetAllAdTariffsUseCaseTest {

    @InjectMocks
    private GetAllAdTariffsUseCase useCase;

    @Mock
    private AdTariffRepository tariffRepository;

    @Mock
    private AdTariffResponseMapper responseMapper;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void returnsPagedTariffsWithActiveCount() {
        AdTariff tariff = AdTariff.create(
                TariffName.of("Basic"), "", PlacementType.BANNER, TariffPeriod.WEEK,
                Price.ofMinor(100L, "TMT"), 10, 1, 1, 0);

        AdTariffResponse activeResponse = mock(AdTariffResponse.class);
        when(activeResponse.isActive()).thenReturn(true);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(tariffRepository.findAll(any(Pageable.class))).thenReturn(Flux.just(tariff));
        when(responseMapper.toResponse(tariff)).thenReturn(Mono.just(activeResponse));
        when(tariffRepository.count()).thenReturn(Mono.just(1L));

        StepVerifier.create(useCase.execute(new GetAllAdTariffsUseCase.Query(1, 10)))
                .assertNext(list -> {
                    assertEquals(1, list.getTariffs().size());
                    assertEquals(1L, list.getActiveCount());
                })
                .verifyComplete();
    }

    @Test
    void exposesBoundContext() {
        assertEquals("advertising.admin", useCase.getBoundContext());
    }
}
