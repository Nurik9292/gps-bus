package biz.ugur.busroutebackend.transport.application.services;

import biz.ugur.busroutebackend.transport.application.dto.BusInfoDTO;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.application.dto.VehiclePositionDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Service for enriching GPS position data with route information.
 * Orchestrates domain services and repositories to provide enriched vehicle data.
 *
 * @author Claude Code
 * @since 2025-10-30
 */
public interface VehicleDataEnrichmentService {

    /**
     * Enrich GPS positions with route information from cached mapping.
     *
     * @param gpsPositions List of GPS positions to enrich
     * @return Flux of enriched vehicle positions with route information
     */
    Flux<VehiclePositionDTO> enrichGpsPositionsWithRouteInfo(List<GpsPositionDTO> gpsPositions);

    /**
     * Update the device-to-route mapping cache with fresh bus info data.
     *
     * @param busInfoList List of bus info containing device-route mappings
     * @return Mono that completes when cache is updated
     */
    Mono<Void> updateDeviceRouteMappingCache(List<BusInfoDTO> busInfoList);
}
