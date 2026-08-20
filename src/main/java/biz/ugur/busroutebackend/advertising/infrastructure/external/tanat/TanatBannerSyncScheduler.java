package biz.ugur.busroutebackend.advertising.infrastructure.external.tanat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(TanatBannerProperties.class)
@Slf4j
public class TanatBannerSyncScheduler {

    private static final Duration TICK_TIMEOUT = Duration.ofMinutes(2);

    @Bean(destroyMethod = "dispose")
    @ConditionalOnProperty(prefix = "external.api.tanat", name = "enabled", havingValue = "true")
    public Disposable tanatBannerSyncTicker(TanatBannerSyncService syncService,
                                            TanatBannerProperties properties) {
        Duration interval = properties.getFetchInterval();
        log.info("[TANAT] banner sync enabled: position={} interval={}s",
                properties.getPositionKey(), interval.toSeconds());

        return Flux.interval(Duration.ZERO, interval, Schedulers.newSingle("tanat-banner-sync"))
                .onBackpressureDrop()
                .concatMap(tick -> syncService.synchronize()
                        .timeout(TICK_TIMEOUT)
                        .doOnError(error -> log.warn("[TANAT] sync tick failed: {}", error.toString()))
                        .onErrorResume(error -> Mono.empty()))
                .subscribe(ignored -> { },
                        error -> log.error("[TANAT] sync ticker terminated: {}", error.toString()));
    }
}
