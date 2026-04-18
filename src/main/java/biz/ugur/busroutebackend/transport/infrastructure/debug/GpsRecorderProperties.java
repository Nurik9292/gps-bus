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

    private int maxDurationSec = 300;
}
