package biz.ugur.busroutebackend.place.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.place.search")
public class PlaceSearchProperties {

    private double similarityThreshold = 0.3;
    private int shortQueryMaxLength = 3;
    private int defaultLimit = 20;
    private int maxLimit = 50;
    private int nearbyDefaultRadiusMeters = 500;
    private int nearbyMaxRadiusMeters = 5000;
}
