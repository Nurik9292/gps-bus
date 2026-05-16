package biz.ugur.busroutebackend.advertising.infrastructure.scheduler;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "advertising.events.partition")
public class AdEventPartitionAlertProperties {

    private boolean enabled = true;
    private int lookaheadMonths = 2;
    private String cron = "0 0 3 * * *";
    private String zone = "UTC";
}
