package biz.ugur.busroutebackend.payment.infrastructure.provider.svepg;

/**
 * Credentials + endpoint for a single sv_epg bank instance.
 * Passed to {@link SvEpgApiClient} per request so that one client type serves all banks.
 */
public record SvEpgCredentials(
        String baseUrl,     // e.g. https://epg.rysgalbank.tm/epg/rest
        String userName,
        String password,
        String terminalId,  // optional — some banks require it, others ignore it
        String pid          // optional — participant id
) {
    public SvEpgCredentials {
        if (baseUrl  == null || baseUrl.isBlank())  throw new IllegalArgumentException("baseUrl required");
        if (userName == null || userName.isBlank()) throw new IllegalArgumentException("userName required");
        if (password == null || password.isBlank()) throw new IllegalArgumentException("password required");
    }
}
