package biz.ugur.busroutebackend.routing.infrastructure.config;

import biz.ugur.busroutebackend.routing.domain.repository.ETARepository;
import biz.ugur.busroutebackend.routing.domain.service.TripOptionComparator;
import biz.ugur.busroutebackend.routing.domain.service.TripOptionValidationService;
import biz.ugur.busroutebackend.routing.domain.service.TripValidationService;
import biz.ugur.busroutebackend.routing.domain.services.ETACalculationService;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripSearchCriteria;
import biz.ugur.busroutebackend.routing.infrastructure.services.LiveETACalculationService;
import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveRedisTemplate;


@Configuration
public class RoutingDomainConfig {

    @Bean
    public ETACalculationService etaCalculationService(VehicleRepository vehicleRepository,
                                                       ETARepository etaRepository,
                                                       ReactiveRedisTemplate<String, Object> redisTemplate,
                                                       DistanceCalculationService distanceService) {
        return new LiveETACalculationService(vehicleRepository, etaRepository, redisTemplate, distanceService);
    }

    @Bean
    public TripValidationService tripValidationService(DistanceCalculationService distanceCalculationService) {
        return new TripValidationService(distanceCalculationService);
    }

    @Bean
    public TripOptionValidationService tripOptionValidationService(DistanceCalculationService distanceCalculationService) {
        return new TripOptionValidationService(distanceCalculationService);
    }

    @Bean
    public TripOptionComparator defaultTripOptionComparator() {
        return new TripOptionComparator(TripSearchCriteria.defaultCriteria());
    }
}