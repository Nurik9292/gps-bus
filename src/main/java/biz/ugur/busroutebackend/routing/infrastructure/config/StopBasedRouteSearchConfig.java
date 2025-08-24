package biz.ugur.busroutebackend.routing.infrastructure.config;

import biz.ugur.busroutebackend.routing.infrastructure.services.StopBasedRouteSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class StopBasedRouteSearchConfig {


    @Bean
    @ConditionalOnProperty(
            value = "routing.stop-based-search.enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public StopBasedRouteSearchService stopBasedRouteSearchService(
            biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService routeCalculationService,
            biz.ugur.busroutebackend.routing.infrastructure.services.NearbyStopsService nearbyStopsService,
            biz.ugur.busroutebackend.routing.application.builders.TransferRouteOptionBuilder transferOptionBuilder,
            biz.ugur.busroutebackend.routing.application.builders.DirectRouteOptionBuilder directOptionBuilder) {

        return new StopBasedRouteSearchService(
                routeCalculationService,
                nearbyStopsService,
                transferOptionBuilder,
                directOptionBuilder
        );
    }
}