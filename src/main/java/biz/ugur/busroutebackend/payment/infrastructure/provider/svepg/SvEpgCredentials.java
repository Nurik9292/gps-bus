package biz.ugur.busroutebackend.payment.infrastructure.provider.svepg;


public record SvEpgCredentials(
        String baseUrl,
        String userName,
        String password,
        String terminalId,
        String pid
) {
    public SvEpgCredentials {
        if (baseUrl  == null || baseUrl.isBlank())  throw new IllegalArgumentException("baseUrl required");
        if (userName == null || userName.isBlank()) throw new IllegalArgumentException("userName required");
        if (password == null || password.isBlank()) throw new IllegalArgumentException("password required");
    }

    @Override
    public String toString() {
        return "SvEpgCredentials[baseUrl=" + baseUrl
                + ", userName=" + userName
                + ", password=***"
                + ", terminalId=" + terminalId
                + ", pid=" + pid + "]";
    }
}
