package biz.ugur.busroutebackend.transport.infrastructure.monitoring.routeswap;

import biz.ugur.busroutebackend.shared.infrastructure.email.AlertQuietHours;
import biz.ugur.busroutebackend.shared.infrastructure.email.EmailNotificationService;
import biz.ugur.busroutebackend.transport.application.services.ExternalApiService;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteSwapAuditRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "business.route-swap-detector", name = "enabled",
        havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(RouteSwapProperties.class)
public class RouteSwapDetectorConfig {

    @Bean
    public RouteSwapTap routeSwapTap() {
        return new RouteSwapTap();
    }

    @Bean
    public RouteSwapGeoDictionary routeSwapGeoDictionary(BusRouteRepository busRouteRepository,
                                                         RouteSwapProperties properties) {
        return new RouteSwapGeoDictionary(busRouteRepository, properties);
    }

    @Bean(destroyMethod = "shutdown")
    public RouteSwapMonitor routeSwapMonitor(RouteSwapProperties properties,
                                             RouteSwapGeoDictionary dictionary,
                                             EmailNotificationService emailService,
                                             AlertQuietHours quietHours,
                                             RouteSwapAuditRepository auditRepository,
                                             RouteSwapTap tap) {
        RouteSwapMonitor monitor = new RouteSwapMonitor(properties, dictionary, emailService, quietHours,
                auditRepository);
        monitor.attachTo(tap);
        monitor.warmupFromPersistedVerdicts();
        dictionary.ensureBuilt();
        return monitor;
    }

    @Bean
    public ProviderAssignmentCrossCheck providerAssignmentCrossCheck(RouteSwapProperties properties,
                                                                     ExternalApiService externalApiService,
                                                                     VehicleRepository vehicleRepository,
                                                                     RouteSwapAuditRepository auditRepository,
                                                                     EmailNotificationService emailService,
                                                                     AlertQuietHours quietHours) {
        return new ProviderAssignmentCrossCheck(properties, externalApiService, vehicleRepository,
                auditRepository, emailService, quietHours, java.time.Clock.systemUTC());
    }
}
