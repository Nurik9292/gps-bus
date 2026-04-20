package biz.ugur.busroutebackend.routing.application.usecase;

import biz.ugur.busroutebackend.routing.domain.repository.RoutingStatisticsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetRoutingStatisticsUseCaseTest {

    @InjectMocks
    private GetRoutingStatisticsUseCase useCase;

    @Mock
    private RoutingStatisticsRepository statisticsRepository;

    @Test
    void returnsStatisticsWithPopularRoutes() {
        when(statisticsRepository.getPopularRoutes(5))
                .thenReturn(Flux.just("29A", "12", "7"));

        StepVerifier.create(useCase.execute())
                .assertNext(response -> {
                    assertEquals("success", response.status());
                    assertNotNull(response.statistics());
                    List<?> routes = (List<?>) response.statistics().get("most_popular_routes");
                    assertEquals(3, routes.size());
                    assertNotNull(response.timestamp());
                })
                .verifyComplete();
    }

    @Test
    void returnsNAPlaceholderWhenNoPopularRoutes() {
        when(statisticsRepository.getPopularRoutes(5))
                .thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute())
                .assertNext(response -> {
                    List<?> routes = (List<?>) response.statistics().get("most_popular_routes");
                    assertEquals(List.of("N/A"), routes);
                })
                .verifyComplete();
    }
}
