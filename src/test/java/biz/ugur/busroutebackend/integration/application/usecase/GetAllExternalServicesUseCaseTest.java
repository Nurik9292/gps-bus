package biz.ugur.busroutebackend.integration.application.usecase;

import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.integration.domain.model.ExternalService;
import biz.ugur.busroutebackend.integration.domain.repository.ExternalServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetAllExternalServicesUseCaseTest {

    @InjectMocks
    private GetAllExternalServicesUseCase useCase;

    @Mock
    private ExternalServiceRepository externalServiceRepository;

    private ExternalService first;
    private ExternalService second;

    @BeforeEach
    void setUp() {
        AdminId admin = AdminId.generate();
        first = ExternalService.create("Alpha", "d1", admin, List.of("/a/**"), 60, false);
        second = ExternalService.create("Beta", "d2", admin, List.of("/b/**"), 30, true);
    }

    @Test
    void executeReturnsListWithTotalsAndActiveCount() {
        when(externalServiceRepository.findAll()).thenReturn(Flux.just(first, second));
        when(externalServiceRepository.count()).thenReturn(Mono.just(2L));
        when(externalServiceRepository.countActive()).thenReturn(Mono.just(2L));

        StepVerifier.create(useCase.execute())
                .assertNext(list -> {
                    assertEquals(2, list.services().size());
                    assertEquals(2L, list.totalCount());
                    assertEquals(2L, list.activeCount());
                })
                .verifyComplete();
    }

    @Test
    void executeActiveOnlyReturnsActiveList() {
        when(externalServiceRepository.findAllActive()).thenReturn(Flux.just(second));
        when(externalServiceRepository.countActive()).thenReturn(Mono.just(1L));

        StepVerifier.create(useCase.executeActiveOnly())
                .assertNext(list -> {
                    assertEquals(1, list.services().size());
                    assertEquals(1L, list.activeCount());
                    assertEquals(1L, list.totalCount());
                })
                .verifyComplete();
    }

    @Test
    void executeReturnsEmptyListWhenRepositoryHasNoServices() {
        when(externalServiceRepository.findAll()).thenReturn(Flux.empty());
        when(externalServiceRepository.count()).thenReturn(Mono.just(0L));
        when(externalServiceRepository.countActive()).thenReturn(Mono.just(0L));

        StepVerifier.create(useCase.execute())
                .assertNext(list -> {
                    assertEquals(0, list.services().size());
                    assertEquals(0L, list.totalCount());
                })
                .verifyComplete();
    }
}
