package biz.ugur.busroutebackend.shared.infrastructure.redis;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "cache.redis.operations")
public class RedisOperationProperties {

    private Duration readTimeout = Duration.ofSeconds(5);

    private Duration writeTimeout = Duration.ofSeconds(5);

    private Duration batchTimeout = Duration.ofSeconds(10);
}
