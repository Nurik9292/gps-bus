package biz.ugur.busroutebackend.transport.application.usecase.pipeline;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class GpsPositionResolver {

    private final PipelineTracer pipelineTracer;

    public GpsPositionResolver(PipelineTracer pipelineTracer) {
        this.pipelineTracer = pipelineTracer;
    }

    public Map<String, GpsPositionDTO> resolveLatestByDevice(List<GpsPositionDTO> validPositions,
                                                              Map<String, Vehicle> existingVehicles) {
        Map<String, GpsPositionDTO> latestPositionsByDevice = new LinkedHashMap<>();
        Map<String, List<GpsPositionDTO>> duplicatesByDevice = new HashMap<>();

        for (GpsPositionDTO position : validPositions) {
            String deviceId = position.getDeviceId();
            GpsPositionDTO existing = latestPositionsByDevice.get(deviceId);

            if (existing != null) {
                duplicatesByDevice.computeIfAbsent(deviceId, k -> new ArrayList<>()).add(existing);
            }

            if (existing == null
                    || position.getFixTime() != null
                    && (existing.getFixTime() == null
                        || position.getFixTime().isAfter(existing.getFixTime()))) {
                latestPositionsByDevice.put(deviceId, position);
            }
        }

        traceWinners(latestPositionsByDevice, duplicatesByDevice, existingVehicles);
        logDuplicateSources(latestPositionsByDevice, duplicatesByDevice, existingVehicles);
        logProviderSwitches(latestPositionsByDevice, existingVehicles);

        log.debug("[GPS_PIPELINE] PROCESS_BATCH input={} unique_vehicles={} cross_provider_duplicates={}",
                validPositions.size(), latestPositionsByDevice.size(), duplicatesByDevice.size());

        return latestPositionsByDevice;
    }

    private void traceWinners(Map<String, GpsPositionDTO> latestByDevice,
                               Map<String, List<GpsPositionDTO>> duplicates,
                               Map<String, Vehicle> existingVehicles) {
        for (Map.Entry<String, GpsPositionDTO> e : latestByDevice.entrySet()) {
            GpsPositionDTO winner = e.getValue();
            Vehicle v = existingVehicles.get(e.getKey());
            String plate = v != null ? v.getLicensePlate() : null;
            int candidatesCount = 1 + (duplicates.containsKey(e.getKey())
                    ? duplicates.get(e.getKey()).size() : 0);
            pipelineTracer.traceBatchWinner(e.getKey(), plate,
                    winner.getGpsProvider() != null ? winner.getGpsProvider().name() : "UNKNOWN",
                    candidatesCount,
                    String.valueOf(winner.getFixTime()));
        }
    }

    private void logDuplicateSources(Map<String, GpsPositionDTO> latestByDevice,
                                      Map<String, List<GpsPositionDTO>> duplicates,
                                      Map<String, Vehicle> existingVehicles) {
        for (var entry : duplicates.entrySet()) {
            String deviceId = entry.getKey();
            GpsPositionDTO winner = latestByDevice.get(deviceId);
            List<GpsPositionDTO> losers = entry.getValue();
            losers.add(winner);
            Vehicle v = existingVehicles.get(deviceId);
            String plate = v != null ? v.getLicensePlate() : "?";

            StringBuilder sb = new StringBuilder();
            for (GpsPositionDTO p : losers) {
                double distFromWinner = (p == winner || p.getLatitude() == null || winner.getLatitude() == null)
                        ? 0.0
                        : DistanceCalculationService.haversineDistanceMeters(
                                p.getLatitude(), p.getLongitude(),
                                winner.getLatitude(), winner.getLongitude());
                sb.append(String.format(" [%s %s lat=%.5f lon=%.5f fixTime=%s dist=%.0fm]",
                        p.getGpsProvider(), p == winner ? "WINNER" : "loser",
                        p.getLatitude() != null ? p.getLatitude() : 0.0,
                        p.getLongitude() != null ? p.getLongitude() : 0.0,
                        p.getFixTime(),
                        distFromWinner));
            }
            log.info("[GPS_PIPELINE] GPS_DUPLICATE_SOURCES device={} plate={} winner={} candidates={}{}",
                    deviceId, plate, winner.getGpsProvider(), losers.size(), sb);
        }
    }

    private void logProviderSwitches(Map<String, GpsPositionDTO> latestByDevice,
                                      Map<String, Vehicle> existingVehicles) {
        for (GpsPositionDTO winner : latestByDevice.values()) {
            String deviceId = winner.getDeviceId();
            Vehicle v = existingVehicles.get(deviceId);
            String plate = v != null ? v.getLicensePlate() : "?";
            String storedProvider = v != null && v.getGpsProvider() != null ? v.getGpsProvider().getCode() : null;
            String newProvider = winner.getGpsProvider() != null ? winner.getGpsProvider().getCode() : null;
            if (storedProvider != null && newProvider != null && !storedProvider.equals(newProvider)) {
                log.info("[GPS_PIPELINE] GPS_PROVIDER_SWITCH device={} plate={} from={} to={} newLat={} newLon={}",
                        deviceId, plate, storedProvider, newProvider,
                        winner.getLatitude(), winner.getLongitude());
            }
        }
    }
}
