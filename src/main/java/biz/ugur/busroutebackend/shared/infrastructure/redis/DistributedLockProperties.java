package biz.ugur.busroutebackend.shared.infrastructure.redis;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;


@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "distributed-lock")
public class DistributedLockProperties {


    private boolean enabled = true;

    private Duration defaultTimeout = Duration.ofMinutes(3);

    private Duration acquireTimeout = Duration.ofSeconds(10);

    private Duration retryInterval = Duration.ofMillis(100);

    private String keyPrefix = "lock:";

    private String instanceId;

    private Duration gpsSchedulerLockTimeout = Duration.ofMinutes(3);
}
