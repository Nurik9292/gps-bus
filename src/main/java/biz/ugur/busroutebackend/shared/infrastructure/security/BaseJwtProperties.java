package biz.ugur.busroutebackend.shared.infrastructure.security;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;


@Getter
@Setter
public abstract class BaseJwtProperties {

    private String secret;
    private Duration accessTokenExpiration;
    private Duration refreshTokenExpiration;
    private String issuer;

    public void setSecret(String secret) {
        if (secret != null && secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters long, got: " + secret.length());
        }
        this.secret = secret;
    }

    public void setAccessTokenExpiration(Duration accessTokenExpiration) {
        if (accessTokenExpiration != null && accessTokenExpiration.isNegative()) {
            throw new IllegalArgumentException("Access token expiration must be positive");
        }
        this.accessTokenExpiration = accessTokenExpiration;
    }

    public void setRefreshTokenExpiration(Duration refreshTokenExpiration) {
        if (refreshTokenExpiration != null && refreshTokenExpiration.isNegative()) {
            throw new IllegalArgumentException("Refresh token expiration must be positive");
        }
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public void setIssuer(String issuer) {
        if (issuer != null && issuer.trim().isEmpty()) {
            throw new IllegalArgumentException("JWT issuer cannot be empty");
        }
        this.issuer = issuer != null ? issuer.trim() : null;
    }

    @jakarta.annotation.PostConstruct
    public void validate() {
        String context = getContext();

        if (secret == null) {
            throw new IllegalArgumentException(context + " JWT secret is required");
        }
        if (accessTokenExpiration == null) {
            throw new IllegalArgumentException(context + " access token expiration is required");
        }
        if (refreshTokenExpiration == null) {
            throw new IllegalArgumentException(context + " refresh token expiration is required");
        }
        if (issuer == null) {
            throw new IllegalArgumentException(context + " JWT issuer is required");
        }

        if (refreshTokenExpiration.compareTo(accessTokenExpiration) <= 0) {
            throw new IllegalArgumentException(
                    String.format("%s refresh token expiration (%s) must be longer than access token expiration (%s)",
                            context, refreshTokenExpiration, accessTokenExpiration)
            );
        }
    }

    protected abstract String getContext();

    @Override
    public String toString() {
        return String.format("%s{issuer='%s', accessTokenExpiration=%s, refreshTokenExpiration=%s}",
                getClass().getSimpleName(), issuer, accessTokenExpiration, refreshTokenExpiration);
    }
}