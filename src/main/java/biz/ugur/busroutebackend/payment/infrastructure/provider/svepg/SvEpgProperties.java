package biz.ugur.busroutebackend.payment.infrastructure.provider.svepg;

import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "payment.svepg")
public class SvEpgProperties {

    private Map<PaymentProvider, Bank> banks = new EnumMap<>(PaymentProvider.class);

    private int timeoutMillis = 15_000;

    @Data
    public static class Bank {
        private boolean enabled = false;
        private String baseUrl;
        private String userName;
        @ToString.Exclude
        private String password;
        private String terminalId;
        private String pid;
    }

    public boolean isEnabled(PaymentProvider provider) {
        Bank b = banks.get(provider);
        return b != null && b.isEnabled();
    }

    public SvEpgCredentials credentialsFor(PaymentProvider provider) {
        Bank b = banks.get(provider);
        if (b == null || !b.isEnabled()) {
            throw new IllegalStateException(
                    "sv_epg provider not configured or disabled: " + provider);
        }
        return new SvEpgCredentials(b.getBaseUrl(), b.getUserName(), b.getPassword(),
                b.getTerminalId(), b.getPid());
    }
}
