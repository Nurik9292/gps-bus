package biz.ugur.busroutebackend.transport.infrastructure.services;

import biz.ugur.busroutebackend.transport.application.dto.BusInfoDTO;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.application.dto.VehiclePositionDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@Slf4j
public class VehicleDataEnrichmentService {

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private static final String DEVICE_ROUTE_MAPPING_KEY = "device:route:mapping";
    private static final Duration CACHE_TTL = Duration.ofHours(25);

    public VehicleDataEnrichmentService(ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Flux<VehiclePositionDTO> enrichGpsPositionsWithRouteInfo(List<GpsPositionDTO> gpsPositions) {
        return getDeviceRouteMapping()
                .flatMapMany(routeMapping ->
                        Flux.fromIterable(gpsPositions)
                                .map(gpsPosition -> enrichSinglePosition(gpsPosition, routeMapping))
                                .filter(dto -> dto.getRouteNumber() != null)
                );
    }

    public Mono<Void> updateDeviceRouteMappingCache(List<BusInfoDTO> busInfoList) {
        Map<String, String> deviceToRouteMap = busInfoList.stream()
                .filter(busInfo -> busInfo.getDeviceId() != null && busInfo.getRouteNumber() != null)
                .collect(Collectors.toMap(
                        BusInfoDTO::getDeviceId,
                        BusInfoDTO::getRouteNumber,
                        (existing, replacement) -> replacement
                ));

        log.info("Updating device-route mapping cache with {} entries", deviceToRouteMap.size());

        return redisTemplate.opsForValue()
                .set(DEVICE_ROUTE_MAPPING_KEY, deviceToRouteMap, CACHE_TTL)
                .then()
                .doOnSuccess(unused -> log.debug("Device-route mapping cache updated successfully"))
                .doOnError(error -> log.error("Failed to update device-route mapping cache", error));
    }

    private Mono<Map<String, String>> getDeviceRouteMapping() {
        return redisTemplate.opsForValue()
                .get(DEVICE_ROUTE_MAPPING_KEY)
                .map(value -> {
                    if (value instanceof Map<?, ?> rawMap) {
                        Map<String, String> safeMap = new HashMap<>(rawMap.size());
                        rawMap.forEach((k, v) ->
                                safeMap.put(String.valueOf(k), String.valueOf(v))
                        );
                        return safeMap;
                    }
                    return Map.<String, String>of();
                })
                .switchIfEmpty(Mono.just(Map.of()))
                .doOnNext(mapping -> log.trace(
                        "Retrieved device-route mapping with {} entries", mapping.size()
                ));
    }

    private VehiclePositionDTO enrichSinglePosition(GpsPositionDTO gpsPosition, Map<String, String> routeMapping) {
        String routeNumber = routeMapping.get(gpsPosition.getDeviceId());

        String vehicleId = generateVehicleId(gpsPosition);

        VehiclePositionDTO enrichedDto = new VehiclePositionDTO();
        enrichedDto.setVehicleId(vehicleId);
        enrichedDto.setDeviceId(gpsPosition.getDeviceId());
        enrichedDto.setLicensePlate(gpsPosition.getVehicleName());
        enrichedDto.setRouteNumber(routeNumber);
        enrichedDto.setCurrentLatitude(gpsPosition.getLatitude());
        enrichedDto.setCurrentLongitude(gpsPosition.getLongitude());
        enrichedDto.setSpeedKmh(gpsPosition.getSpeed());
        enrichedDto.setIsInMotion(gpsPosition.getMotion());
        enrichedDto.setIsActive(true);
        enrichedDto.setLastPositionUpdate(LocalDateTime.now());

        return enrichedDto;
    }

    private String generateVehicleId(GpsPositionDTO gpsPosition) {
        return "vehicle-" + gpsPosition.getDeviceId();
    }
}
