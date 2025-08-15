package biz.ugur.busroutebackend.client.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;

@Getter
@EqualsAndHashCode(callSuper = false)
public class Otp extends ValueObject {

    private static final int OTP_LENGTH = 5;
    private static final int EXPIRY_MINUTES = 5;

    private final String code;
    private final Instant generatedAt;
    private final Instant expiresAt;
    private final boolean isVerified;

    private Otp(String code, Instant generatedAt, boolean isVerified) {
        this.code = Objects.requireNonNull(code, "OTP code cannot be null");
        this.generatedAt = Objects.requireNonNull(generatedAt, "Generated time cannot be null");
        this.expiresAt = generatedAt.plusSeconds(EXPIRY_MINUTES * 60);
        this.isVerified = isVerified;
        validateCode(code);
    }

    public static Otp generate() {
        String code = String.format("%05d", (int)(Math.random() * 100000));
        return new Otp(code, Instant.now(), false);
    }

    public static Otp of(String code, Instant generatedAt, boolean isVerified) {
        return new Otp(code, generatedAt, isVerified);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean verify(String inputCode) {
        return !isExpired() && !isVerified && code.equals(inputCode);
    }

    public Otp markAsVerified() {
        return new Otp(this.code, this.generatedAt, true);
    }

    private void validateCode(String code) {
        if (code.length() != OTP_LENGTH || !code.matches("\\d{5}")) {
            throw new IllegalArgumentException("OTP must be exactly 5 digits");
        }
    }

    @Override
    public String toString() {
        return "Otp{code='***', generatedAt=" + generatedAt + ", isVerified=" + isVerified + "}";
    }
}