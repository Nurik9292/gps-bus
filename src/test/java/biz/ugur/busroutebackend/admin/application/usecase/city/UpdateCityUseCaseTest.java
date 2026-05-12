package biz.ugur.busroutebackend.admin.application.usecase.city;

import biz.ugur.busroutebackend.admin.application.dto.city.CityUpdate;
import biz.ugur.busroutebackend.admin.domain.model.City;
import biz.ugur.busroutebackend.admin.domain.repository.CityRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.CityId;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class UpdateCityUseCaseTest {

    @InjectMocks
    private UpdateCityUseCase useCase;

    @Mock
    private CityRepository cityRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void updatesNameAndDisplayOrderWhenNameUnique() {
        City existing = City.create("Mary", "Mary", 1);
        CityUpdate update = new CityUpdate(existing.getId().getValue(), "Dashoguz", "Daşoguz", null, 2, null, null, false);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(cityRepository.findById(existing.getId())).thenReturn(Mono.just(existing));
        when(cityRepository.existsByNameAndIdNot("Dashoguz", existing.getId())).thenReturn(Mono.just(false));
        when(cityRepository.save(any(City.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(update)))
                .assertNext(result -> {
                    assertEquals("Dashoguz", result.name());
                    assertEquals(2, result.displayOrder());
                })
                .verifyComplete();

        verify(cityRepository).save(any(City.class));
    }

    @Test
    void errorsWhenRenameCollidesWithExistingCity() {
        City existing = City.create("Mary", "Mary", 1);
        CityUpdate update = new CityUpdate(existing.getId().getValue(), "Ashgabat", "Aşgabat", null, 0, null, null, false);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(cityRepository.findById(existing.getId())).thenReturn(Mono.just(existing));
        when(cityRepository.existsByNameAndIdNot("Ashgabat", existing.getId())).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.execute(Mono.just(update)))
                .expectErrorSatisfies(err -> {
                    assertInstanceOf(IllegalArgumentException.class, err);
                    assertEquals("City already exists with name: Ashgabat", err.getMessage());
                })
                .verify();

        verify(cityRepository, never()).save(any());
    }

    @Test
    void deactivatesCityWhenIsActiveFalse() {
        City existing = City.create("Mary", "Mary", 1);
        CityUpdate update = new CityUpdate(existing.getId().getValue(), "Mary", "Mary", false, 1, null, null, false);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(cityRepository.findById(existing.getId())).thenReturn(Mono.just(existing));
        when(cityRepository.save(any(City.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(Mono.just(update)))
                .assertNext(result -> assertFalse(result.isActive()))
                .verifyComplete();
    }

    @Test
    void errorsWhenCityNotFound() {
        CityUpdate update = new CityUpdate(CityId.generate().getValue(), "X", "X", null, 0, null, null, false);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(cityRepository.findById(any(CityId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(Mono.just(update)))
                .expectErrorSatisfies(err -> assertInstanceOf(IllegalArgumentException.class, err))
                .verify();
    }

    @Test
    void exposesAdminBoundContext() {
        assertEquals("admin", useCase.getBoundContext());
    }
}
