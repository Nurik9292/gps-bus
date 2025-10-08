package biz.ugur.busroutebackend.transport.scheduler;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.scheduler")
public class SchedulerProperties {

    private GpsConfig gps = new GpsConfig();

    @Getter
    @Setter
    public static class GpsConfig {

        private int batchSize = 100;

        private int parallelWorkers = 4;

        private Duration batchTimeout = Duration.ofSeconds(30);

        private Duration totalTimeout = Duration.ofSeconds(60);
    }
}
