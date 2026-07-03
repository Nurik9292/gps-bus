package biz.ugur.busroutebackend.transport.infrastructure.debug;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gps.recorder")
public class GpsRecorderProperties {

    private boolean enabled = false;

    private String outputDir = "logs/fixtures";

    private int maxDurationSec = 3600;

    private boolean campaignEnabled = false;

    private int sessionDurationSec = 3600;

    private String sessionPrefix = "campaign";

    private int rollCheckIntervalSec = 30;

    private java.util.List<String> routes = java.util.List.of();
}
