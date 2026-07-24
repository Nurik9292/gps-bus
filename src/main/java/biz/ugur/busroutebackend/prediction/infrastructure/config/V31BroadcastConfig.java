package biz.ugur.busroutebackend.prediction.infrastructure.config;

import biz.ugur.busroutebackend.prediction.broadcast.V31BroadcastLoop;
import biz.ugur.busroutebackend.prediction.broadcast.V31BroadcastProperties;
import biz.ugur.busroutebackend.prediction.broadcast.V31CityZoneProperties;
import biz.ugur.busroutebackend.prediction.broadcast.V31FrameSink;
import biz.ugur.busroutebackend.prediction.core.CoreConfig;
import biz.ugur.busroutebackend.prediction.core.RouteTopology;
import biz.ugur.busroutebackend.prediction.shadow.V31RouteLines;
import biz.ugur.busroutebackend.prediction.shadow.V31ShadowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import biz.ugur.busroutebackend.interfaces.websocket.WsOutboundStreamBuilder;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.PredictionBroadcaster;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "app.prediction.v31.enabled", havingValue = "true")
@EnableConfigurationProperties({V31BroadcastProperties.class, V31CityZoneProperties.class})
public class V31BroadcastConfig {

    private static final Logger log = LoggerFactory.getLogger(V31BroadcastConfig.class);

    @Bean
    public V31FrameSink v31FrameSink() {
        return new V31FrameSink();
    }

    @Bean
    public V31BroadcastLoop v31BroadcastLoop(V31ShadowService shadow,
                                             V31BroadcastProperties props,
                                             V31CityZoneProperties zones,
                                             V31RouteLines lines,
                                             V31FrameSink sink,
                                             ObjectMapper mapper,
                                             Clock v31Clock,
                                             @Value("${app.prediction.v31.log-dir:logs/ws_pred_v31}")
                                             String logDir) {
        lines.zoneForRoute((routeKey, routeNumber) -> {
            V31CityZoneProperties.Zone z = zoneOf(zones, routeKey, routeNumber);
            return z == null ? null
                    : new RouteTopology.CityZone(z.getDLat(), z.getDLon(), z.getPLat(), z.getPLon());
        });
        shadow.configForRoute((routeKey, routeNumber) -> {
            V31CityZoneProperties.Zone z = zoneOf(zones, routeKey, routeNumber);
            return z == null ? CoreConfig.defaults()
                    : CoreConfig.defaults().withCityZoneParams(z.getRDeep(), z.getRPlateau(),
                        z.getTDwellSec(), z.getM(), z.getGSec(), z.getTCityExitMinSpanSec(),
                        z.getKExit(), z.getDeltaRMeters(), z.getTPostBoundaryGuardSec(),
                        z.getKConfirmPostBoundary(), z.getEpsMidlineMeters());
        });
        return new V31BroadcastLoop(shadow, props, sink, mapper, v31Clock, Path.of(logDir));
    }

    private static V31CityZoneProperties.Zone zoneOf(V31CityZoneProperties zones,
                                                     String routeKey, String routeNumber) {
        V31CityZoneProperties.Zone byId = zones.getRoutes().get(routeKey);
        return byId != null ? byId : zones.getRoutes().get(routeNumber);
    }

    @Bean(destroyMethod = "dispose")
    public Disposable v31LiveWiring(V31BroadcastProperties props, V31FrameSink sink,
                                    WsOutboundStreamBuilder builder,
                                    PredictionBroadcaster legacyBroadcaster) {
        if (props.getBroadcast() == V31BroadcastProperties.Mode.LIVE) {
            builder.v31Frames(sink::asFlux);
            legacyBroadcaster.v31LiveRouteSuppressor(props::routeInScope);
            log.info("v31 LIVE cutover: легаси-вещание подавлено для allowlist={}",
                    props.getRoutesAllowlist().isEmpty() ? "все маршруты" : props.getRoutesAllowlist());
        }
        return Flux.interval(Duration.ofMinutes(1))
                .subscribe(t -> log.info("[WS] активных соединений: {}", builder.activeSessions()),
                        err -> log.debug("ws gauge stopped: {}", err.getMessage()));
    }

    @Bean(destroyMethod = "dispose")
    public Disposable v31BroadcastTicker(V31BroadcastLoop loop, V31BroadcastProperties props) {
        if (props.getBroadcast() == V31BroadcastProperties.Mode.OFF) {
            log.info("v31 broadcast: OFF (тикер не запущен)");
            return () -> { };
        }
        long periodMs = Math.round(1000.0 / Math.max(props.getWBroadcastHz(), 0.1));
        log.info("v31 broadcast: {} тик={}мс allowlist={}",
                props.getBroadcast(), periodMs, props.getRoutesAllowlist());
        return Flux.interval(Duration.ofMillis(periodMs), Schedulers.newSingle("v31-bcast"))
                .onBackpressureDrop()
                .subscribe(t -> {
                    try {
                        loop.tick();
                    } catch (Exception e) {
                        log.warn("v31 broadcast tick failed: {}", e.getMessage());
                    }
                }, err -> log.error("v31 broadcast ticker terminated: {}", err.getMessage()));
    }
}
