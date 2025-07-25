package biz.ugur.busroutebackend.routing.infrastructure.config;

import biz.ugur.busroutebackend.routing.domain.services.ETACalculationService;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService;
import biz.ugur.busroutebackend.routing.infrastructure.services.GraphRouteCalculationService;
import biz.ugur.busroutebackend.routing.infrastructure.services.LiveETACalculationService;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.r2dbc.core.DatabaseClient;

@Configuration
public class RoutingDomainConfig {

    @Bean
    public RouteCalculationService routeCalculationService(BusStopRepository busStopRepository,
                                                           BusRouteRepository busRouteRepository,
                                                           DatabaseClient databaseClient,
                                                           ReactiveRedisTemplate<String, Object> redisTemplate) {
        return new GraphRouteCalculationService(
                busStopRepository,
                busRouteRepository,
                databaseClient,
                redisTemplate);
    }

    @Bean
    public ETACalculationService etaCalculationService(VehicleRepository vehicleRepository,
                                                       DatabaseClient databaseClient,
                                                       ReactiveRedisTemplate<String, Object> redisTemplate) {
        return new LiveETACalculationService(vehicleRepository, databaseClient, redisTemplate);
    }
}