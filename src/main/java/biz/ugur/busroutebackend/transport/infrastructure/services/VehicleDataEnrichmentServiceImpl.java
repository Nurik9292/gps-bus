package biz.ugur.busroutebackend.transport.infrastructure.services;

import biz.ugur.busroutebackend.transport.application.dto.BusInfoDTO;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.application.dto.VehiclePositionDTO;
import biz.ugur.busroutebackend.transport.application.services.VehicleDataEnrichmentService;
import biz.ugur.busroutebackend.transport.domain.repository.DeviceRouteMappingRepository;
import biz.ugur.busroutebackend.transport.application.services.VehicleEnrichmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@Slf4j
public class VehicleDataEnrichmentServiceImpl implements VehicleDataEnrichmentService {

    private final VehicleEnrichmentService vehicleEnrichmentService;
    private final DeviceRouteMappingRepository deviceRouteMappingRepository;

    public VehicleDataEnrichmentServiceImpl(
            VehicleEnrichmentService vehicleEnrichmentService,
            DeviceRouteMappingRepository deviceRouteMappingRepository) {
        this.vehicleEnrichmentService = vehicleEnrichmentService;
        this.deviceRouteMappingRepository = deviceRouteMappingRepository;
    }

    @Override
    public Flux<VehiclePositionDTO> enrichGpsPositionsWithRouteInfo(List<GpsPositionDTO> gpsPositions) {
        return deviceRouteMappingRepository.getMapping()
                .flatMapMany(routeMapping ->
                        Flux.fromIterable(gpsPositions)
                                .map(gpsPosition -> vehicleEnrichmentService.enrichPosition(gpsPosition, routeMapping))
                                .filter(vehicleEnrichmentService::hasValidRoute)
                );
    }

    @Override
    public Mono<Void> updateDeviceRouteMappingCache(List<BusInfoDTO> busInfoList) {
        Map<String, String> deviceToRouteMap = busInfoList.stream()
                .filter(busInfo -> busInfo.getDeviceId() != null && busInfo.getRouteNumber() != null)
                .collect(Collectors.toMap(
                        BusInfoDTO::getDeviceId,
                        BusInfoDTO::getRouteNumber,
                        (existing, replacement) -> replacement
                ));

        log.info("Updating device-route mapping with {} entries", deviceToRouteMap.size());

        return deviceRouteMappingRepository.updateMapping(deviceToRouteMap);
    }
}
