package biz.ugur.busroutebackend.transport.application.services;

import biz.ugur.busroutebackend.transport.infrastructure.messaging.VehiclePositionWebSocketMessage;
import reactor.core.publisher.Mono;

import java.util.List;

public interface VehicleEtaEnricherService {

    Mono<List<VehiclePositionWebSocketMessage.NextStopEta>> calculateNextStopsEta(
            String vehicleId,
            String routeNumber,
            Double latitude,
            Double longitude,
            Double speedKmh,
            int maxStops
    );

    Mono<VehiclePositionWebSocketMessage> enrichWithEta(VehiclePositionWebSocketMessage message);
}
