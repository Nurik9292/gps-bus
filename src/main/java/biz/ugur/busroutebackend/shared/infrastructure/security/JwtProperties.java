package biz.ugur.busroutebackend.shared.infrastructure.security;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.security.jwt")
@Component
public class JwtProperties {

    private String secret;
    private Duration accessTokenExpiration;
    private Duration refreshTokenExpiration;
    private String issuer;

    public JwtProperties() {}



    public String secret() { return secret; }
    public Duration accessTokenExpiration() { return accessTokenExpiration; }
    public Duration refreshTokenExpiration() { return refreshTokenExpiration; }
    public String issuer() { return issuer; }

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
        if (secret == null) {
            throw new IllegalArgumentException("JWT secret is required");
        }
        if (accessTokenExpiration == null) {
            throw new IllegalArgumentException("Access token expiration is required");
        }
        if (refreshTokenExpiration == null) {
            throw new IllegalArgumentException("Refresh token expiration is required");
        }
        if (issuer == null) {
            throw new IllegalArgumentException("JWT issuer is required");
        }

        if (refreshTokenExpiration.compareTo(accessTokenExpiration) <= 0) {
            throw new IllegalArgumentException(
                    String.format("Refresh token expiration (%s) must be longer than access token expiration (%s)",
                            refreshTokenExpiration, accessTokenExpiration)
            );
        }
    }

    @Override
    public String toString() {
        return String.format("JwtProperties{issuer='%s', accessTokenExpiration=%s, refreshTokenExpiration=%s}",
                issuer, accessTokenExpiration, refreshTokenExpiration);
    }
}