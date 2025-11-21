package biz.ugur.busroutebackend.transport.application.services;

import biz.ugur.busroutebackend.transport.application.dto.BusInfoDTO;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ExternalApiService {

    Mono<List<GpsPositionDTO>> fetchVehiclePositionsByIds(List<String> deviceIds);

    Mono<List<BusInfoDTO>> fetchAllBusInfo();

    Mono<Boolean> gpsHealthCheck();

    Mono<Boolean> busInfoHealthCheck();
}
