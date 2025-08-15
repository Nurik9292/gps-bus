package biz.ugur.busroutebackend.client.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;

@Getter
@EqualsAndHashCode(callSuper = false)
public class ClientTokens extends ValueObject {

    private final String accessToken;
    private final String refreshToken;
    private final Instant issuedAt;
    private final Instant expiresAt;

    private ClientTokens(String accessToken, String refreshToken,
                         Instant issuedAt, Instant expiresAt) {
        this.accessToken = Objects.requireNonNull(accessToken, "Access token cannot be null");
        this.refreshToken = Objects.requireNonNull(refreshToken, "Refresh token cannot be null");
        this.issuedAt = Objects.requireNonNull(issuedAt, "Issued time cannot be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "Expires time cannot be null");
    }

    public static ClientTokens of(String accessToken, String refreshToken,
                                  Instant issuedAt, Instant expiresAt) {
        return new ClientTokens(accessToken, refreshToken, issuedAt, expiresAt);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    @Override
    public String toString() {
        return "ClientTokens{issuedAt=" + issuedAt + ", expiresAt=" + expiresAt + "}";
    }
}