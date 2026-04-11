package biz.ugur.busroutebackend.transport.infrastructure.config;

import biz.ugur.busroutebackend.transport.domain.service.GpsOutlierDetector;
import biz.ugur.busroutebackend.transport.domain.service.LicensePlateExtractor;
import biz.ugur.busroutebackend.transport.domain.service.PositionChangeDetector;
import biz.ugur.busroutebackend.transport.domain.service.VehicleValidationService;
import biz.ugur.busroutebackend.transport.domain.services.VehicleAssignmentExpirationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class TransportDomainConfig {

    @Bean
    public VehicleValidationService vehicleValidationService() {
        return new VehicleValidationService();
    }

    @Bean
    public LicensePlateExtractor licensePlateExtractor(VehicleValidationService validationService) {
        return new LicensePlateExtractor(validationService);
    }

    @Bean
    public PositionChangeDetector positionChangeDetector(PositionChangeProperties properties) {
        return new PositionChangeDetector(
                properties.getPositionDeltaThreshold(),
                properties.getSpeedDeltaThresholdKmh(),
                properties.getMinSpeedForMotionKmh()
        );
    }

    @Bean
    public GpsOutlierDetector gpsOutlierDetector(GpsOutlierDetectionProperties properties) {
        return new GpsOutlierDetector(
                properties.isEnabled(),
                properties.getMaxImpliedSpeedKmh(),
                properties.getMinTimeDifference().toSeconds(),
                properties.getMaxTimeDifference().toSeconds(),
                properties.getMinDistanceMeters(),
                properties.getMinSpeedForFrozenDetectionKmh()
        );
    }

    @Bean
    public VehicleAssignmentExpirationService vehicleAssignmentExpirationService() {
        return new VehicleAssignmentExpirationService();
    }
}
