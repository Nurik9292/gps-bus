package biz.ugur.busroutebackend.integration.domain.valueobjects;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.security.SecureRandom;
import java.util.Base64;

@Getter
@ToString(exclude = "value") 
@EqualsAndHashCode
public class ApiToken implements Serializable {

    private static final int TOKEN_LENGTH = 48; 
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final String value;

    private ApiToken(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("ApiToken cannot be null or empty");
        }
        if (value.length() < 32) {
            throw new IllegalArgumentException("ApiToken must be at least 32 characters long");
        }
        this.value = value;
    }

    public static ApiToken of(String value) {
        return new ApiToken(value);
    }

    public static ApiToken generate() {
        byte[] randomBytes = new byte[TOKEN_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        String randomPart = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
        return new ApiToken("brt_" + randomPart);
    }

    public String getMaskedValue() {
        if (value.length() <= 12) {
            return "****";
        }
        return value.substring(0, 8) + "..." + value.substring(value.length() - 4);
    }
}
