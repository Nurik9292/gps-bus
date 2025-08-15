package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
public class FixUseCase implements UseCase<Void, Mono<Void>> {

    private final BusRouteRepository busRouteRepository;


    private final ObjectMapper objectMapper = new ObjectMapper();

    public FixUseCase(BusRouteRepository busRouteRepository) {
        this.busRouteRepository = busRouteRepository;
    }

    @Override
    public Mono<Void> execute(Void unused) {
        List<String> routeIds = List.of(
                "route-legacy-154",
        "route-legacy-155",
        "route-legacy-157",
        "route-legacy-160",
        "route-legacy-161"
        );

        return Flux.fromIterable(routeIds)
                .flatMap(routeId ->
                        busRouteRepository.findById(BusRouteId.of(routeId))
                                .flatMap(route -> {
                                    String name =  extractName(route.getRouteName());
                                    route.updateBasicInfo(
                                            route.getRouteNumber(),
                                            name,
                                            "",
                                            "",
                                            route.getRouteColor(),
                                            route.getEstimatedDurationMinutes() == null ? 0 : route.getEstimatedDurationMinutes(),
                                            route.getCityId()
                                    );

                                    route.setTotalDistanceBackwardMeters(route.getTotalDistanceBackwardMeters() == null ? 0 : route.getTotalDistanceBackwardMeters());
                                    route.setTotalDistanceForwardMeters(route.getTotalDistanceForwardMeters() == null ? 0 : route.getTotalDistanceForwardMeters());

                                    return busRouteRepository.save(route);
                                })
                )
                .then();
    }

    private String extractName(String jsonName) {
        Map<String, String> map = null;
        try {
            map = objectMapper.readValue(jsonName, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return map.get("en");
    }
}
