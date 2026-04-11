package biz.ugur.busroutebackend.routing.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "app.route-search")
@Data
public class RouteSearchConfig {

    private double nearbyStopsRadiusKm = 0.8;
    private int maxStopsPerLocation = 8;

    private int maxDirectRoutes = 5;
    private int maxOneTransferRoutes = 8;
    private int maxTwoTransferRoutes = 4;

    private Duration directSearchTimeout = Duration.ofSeconds(15);
    private Duration oneTransferSearchTimeout = Duration.ofSeconds(20);
    private Duration twoTransferSearchTimeout = Duration.ofSeconds(25);
    private Duration totalSearchTimeout = Duration.ofSeconds(30);

    private int maxWalkingTimeMinutes = 15;
    private int maxTwoTransferWalkingTimeMinutes = 18;
    private int maxOneTransferTotalMinutes = 100;
    private int maxTwoTransferTotalMinutes = 150;
    private int maxTransferWaitMinutes = 30;
    private int maxTwoTransferWaitMinutes = 25;

    private double oneTransferMaxDistanceKm = 0.5;
    private double twoTransferMaxDistanceKm = 0.3;

    private Duration cacheTimeout = Duration.ofSeconds(2);
    private Duration cacheTtl = Duration.ofMinutes(30);
}