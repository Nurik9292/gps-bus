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

    private ActiveHours activeHours = new ActiveHours();

    private Duration updateInterval = Duration.ofSeconds(5);

    @Getter
    @Setter
    public static class GpsConfig {

        private int batchSize = 100;

        private int parallelWorkers = 4;

        private Duration batchTimeout = Duration.ofSeconds(10);

        private Duration totalTimeout = Duration.ofSeconds(15);
    }

    @Getter
    @Setter
    public static class ActiveHours {

        private int start = 6;

        private int end = 23;

        private String timezone = "Asia/Ashgabat";
    }
}
