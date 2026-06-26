package biz.ugur.busroutebackend.client.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Objects;
import java.util.regex.Pattern;

@Getter
@EqualsAndHashCode(callSuper = false)
public class Phone extends ValueObject {

    private static final Pattern TURKMENISTAN_PHONE_PATTERN =
            Pattern.compile("^(\\+993|993)?[1-9]\\d{7}$");

    private final String value;
    private final String countryCode;
    private final String number;

    private Phone(String phone) {
        this.value = Objects.requireNonNull(phone, "Phone cannot be null");
        validateFormat(phone);

        if (phone.startsWith("+993")) {
            this.countryCode = "+993";
            this.number = phone.substring(4);
        } else if (phone.startsWith("993")) {
            this.countryCode = "+993";
            this.number = phone.substring(3);
        } else {
            this.countryCode = "+993";
            this.number = phone;
        }
    }

    public static Phone of(String phone) {
        return new Phone(phone);
    }

    public String getFormattedPhone() {
        return countryCode + number;
    }

    public static String canonicalWithoutPlus(String phone) {
        Objects.requireNonNull(phone, "Phone cannot be null");
        return of(phone.trim()).getFormattedPhone().substring(1);
    }

    public static String canonicalWithoutPlusOrNull(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        try {
            return canonicalWithoutPlus(phone);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private void validateFormat(String phone) {
        if (!TURKMENISTAN_PHONE_PATTERN.matcher(phone).matches()) {
            throw new IllegalArgumentException(
                    "Invalid phone format. Expected Turkmenistan phone number: " + phone
            );
        }
    }

    @Override
    public String toString() {
        return "Phone{value='" + value + "'}";
    }
}