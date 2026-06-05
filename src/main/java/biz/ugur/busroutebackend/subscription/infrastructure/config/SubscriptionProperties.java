package biz.ugur.busroutebackend.subscription.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "business.subscription")
@Getter
@Setter
public class SubscriptionProperties {

    private long monthlyAmountMinor = 400;
    private long yearlyAmountMinor = 4000;
    private String currency = "TMT";
    private int paymentSessionMinutes = 30;
    private String paymentReturnUrl;
    private long paymentStatusSyncTimeoutMs = 3000;
}
