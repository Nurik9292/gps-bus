package biz.ugur.busroutebackend.business.domain.valueobjects;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = false)
public class BusinessAddress extends ValueObject {

    private final String country;
    private final String city;
    private final String addressLine;

    private BusinessAddress(String country, String city, String addressLine) {
        this.country = trimOrNull(country);
        this.city = trimOrNull(city);
        this.addressLine = trimOrNull(addressLine);
    }

    public static BusinessAddress of(String country, String city, String addressLine) {
        return new BusinessAddress(country, city, addressLine);
    }

    public static BusinessAddress empty() {
        return new BusinessAddress(null, null, null);
    }

    public boolean isEmpty() {
        return country == null && city == null && addressLine == null;
    }

    private static String trimOrNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
