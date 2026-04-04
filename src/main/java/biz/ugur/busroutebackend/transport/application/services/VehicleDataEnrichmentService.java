package biz.ugur.busroutebackend.transport.application.services;

import biz.ugur.busroutebackend.transport.application.dto.BusInfoDTO;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.application.dto.VehiclePositionDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface VehicleDataEnrichmentService {

    Flux<VehiclePositionDTO> enrichGpsPositionsWithRouteInfo(List<GpsPositionDTO> gpsPositions);

    Mono<Void> updateDeviceRouteMappingCache(List<BusInfoDTO> busInfoList);
}
