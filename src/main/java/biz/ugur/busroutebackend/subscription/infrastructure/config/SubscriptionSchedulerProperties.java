package biz.ugur.busroutebackend.subscription.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "business.subscription.expiry-scheduler")
@Getter
@Setter
public class SubscriptionSchedulerProperties {

    private boolean enabled = false;
    private int batchSize = 100;
}
