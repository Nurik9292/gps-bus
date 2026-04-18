package biz.ugur.busroutebackend.payment.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "payment.scheduler")
public class PaymentSchedulerProperties {

    private boolean enabled = true;

    private long staleAfterSeconds = 180;

    private int batchSize = 50;
}
