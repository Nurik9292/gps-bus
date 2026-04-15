package biz.ugur.busroutebackend.payment.infrastructure.provider.svepg;

import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Loads sv_epg credentials from {@code application.yml} ({@code payment.svepg.*}).
 * One nested {@link Bank} section per {@link PaymentProvider}; only enabled banks
 * are registered as runtime {@link SvEpgCredentials}.
 *
 * <pre>
 * payment:
 *   svepg:
 *     banks:
 *       RYSGAL:
 *         enabled: true
 *         base-url: https://epg.rysgalbank.tm/epg/rest
 *         user-name: ${RYSGAL_API_USER}
 *         password:  ${RYSGAL_API_PASS}
 *         terminal-id: 11014027
 *         pid: 109755
 *       SENAGAT:
 *         enabled: true
 *         base-url: https://epg.senagatbank.com.tm/epg/rest
 *         user-name: ${SENAGAT_API_USER}
 *         password:  ${SENAGAT_API_PASS}
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "payment.svepg")
public class SvEpgProperties {

    /** Per-bank configuration keyed by {@link PaymentProvider}. */
    private Map<PaymentProvider, Bank> banks = new EnumMap<>(PaymentProvider.class);

    /** Default connect/read timeout for HTTP calls to provider (milliseconds). */
    private int timeoutMillis = 15_000;

    @Data
    public static class Bank {
        private boolean enabled = false;
        private String baseUrl;
        private String userName;
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
