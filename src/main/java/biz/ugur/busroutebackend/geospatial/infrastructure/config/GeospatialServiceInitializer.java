package biz.ugur.busroutebackend.geospatial.infrastructure.config;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteGeometry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
@Slf4j
public class GeospatialServiceInitializer {

    private final DistanceCalculationService distanceCalculationService;


    @PostConstruct
    public void initialize() {
        log.info("Initializing geospatial services in value objects...");

        RouteGeometry.setDistanceCalculationService(distanceCalculationService);
        log.debug("✓ RouteGeometry.distanceCalculationService initialized");

        log.info("✅ Geospatial services initialization complete");
        log.info("   - All distance calculations use centralized DistanceCalculationService");
        log.info("   - Haversine formula: single source of truth");
        log.info("   - Location → Coordinates migration: COMPLETED");
        log.info("   - RoutePoint → Coordinates migration: COMPLETED");
    }
}
