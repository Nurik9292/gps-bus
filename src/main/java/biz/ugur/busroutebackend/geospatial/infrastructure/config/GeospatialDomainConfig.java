package biz.ugur.busroutebackend.geospatial.infrastructure.config;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.geospatial.domain.services.RouteGeometryMatchingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeospatialDomainConfig {

    @Value("${business.gps-route-detection.max-distance-for-match-meters:100}")
    private double maxDistanceForMatch;

    @Value("${business.gps-route-detection.deviation-threshold-meters:500}")
    private double deviationThreshold;

    @Bean
    public DistanceCalculationService distanceCalculationService() {
        return new DistanceCalculationService();
    }

    @Bean
    public RouteGeometryMatchingService routeGeometryMatchingService(
            DistanceCalculationService distanceCalculationService) {
        return new RouteGeometryMatchingService(
                distanceCalculationService,
                maxDistanceForMatch,
                deviationThreshold
        );
    }
}
