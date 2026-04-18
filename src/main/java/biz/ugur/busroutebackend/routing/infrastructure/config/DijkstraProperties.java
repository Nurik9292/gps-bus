package biz.ugur.busroutebackend.routing.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "routing.dijkstra")
public class DijkstraProperties {

    private int transferPenaltyMinutes = 5;

    private int maxTransfers = 2;

    private int kPaths = 3;

    private int routePenaltyMinutes = 30;

    private int maxIterations = 50_000;

    private int maxCostMinutes = 180;
}
