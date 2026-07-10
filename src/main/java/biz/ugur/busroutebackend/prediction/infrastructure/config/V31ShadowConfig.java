package biz.ugur.busroutebackend.prediction.infrastructure.config;

import biz.ugur.busroutebackend.prediction.shadow.V31RouteLines;
import biz.ugur.busroutebackend.prediction.shadow.V31ShadowService;
import biz.ugur.busroutebackend.prediction.shadow.V31ShadowTap;

import biz.ugur.busroutebackend.transport.infrastructure.prediction.RouteGeometryCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Clock;

@Configuration
@ConditionalOnProperty(name = "app.prediction.v31.enabled", havingValue = "true")
public class V31ShadowConfig {

    @Bean
    public V31ShadowTap v31ShadowTap() {
        return new V31ShadowTap();
    }

    @Bean
    public V31RouteLines v31RouteLines(RouteGeometryCache cache) {
        return new V31RouteLines(cache);
    }

    @Bean
    public Clock v31Clock() {
        return Clock.systemUTC();
    }

    @Bean(destroyMethod = "shutdown")
    public V31ShadowService v31ShadowService(V31ShadowTap tap, V31RouteLines lines,
                                             Clock v31Clock,
                                             @Value("${app.prediction.v31.log-dir:logs/ws_pred_v31}")
                                             String logDir) {
        return new V31ShadowService(tap, lines, v31Clock, Path.of(logDir));
    }
}
