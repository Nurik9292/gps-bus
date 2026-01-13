package biz.ugur.busroutebackend.shared.infrastructure.external.gps.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "external.api.gps-common.optimization")
public class GpsFetchOptimizationProperties {

    private boolean selectiveFetchEnabled = false;

    private int selectiveFetchThreshold = 10;

    private int parallelFetchConcurrency = 5;

    private boolean logStrategyDecisions = false;
}
