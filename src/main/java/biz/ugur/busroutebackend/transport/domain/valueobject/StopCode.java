package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;


@Getter
@EqualsAndHashCode(callSuper = false)
public class StopCode extends ValueObject {

    private static final Map<String, String> CITY_PREFIXES = Map.of(
            "city-001", "ASH",
            "city-002", "BAL",
            "city-003", "TUR",
            "city-004", "MAR",
            "city-005", "TRK"
    );

    private static final String DEFAULT_PREFIX = "ST";
    private static final Pattern VALID_CODE_PATTERN = Pattern.compile("^[A-Z]{2,3}\\d{3}(\\d{2})?$");

    String value;

    private StopCode(String value) {
        this.value = Objects.requireNonNull(value, "Stop code cannot be null");
        validateFormat(value);
    }



    public static StopCode of(String value) {
        return new StopCode(value);
    }

    public static StopCode generate(String cityId, long sequence) {
        String prefix = getCityPrefix(cityId);
        String code = String.format("%s%03d", prefix, sequence);
        return new StopCode(code);
    }


    public static StopCode generateWithSuffix(String cityId, long sequence, int suffix) {
        String prefix = getCityPrefix(cityId);
        String code = String.format("%s%03d%02d", prefix, sequence, suffix);
        return new StopCode(code);
    }


    public String getCityPrefix() {
        if (value.length() >= 3) {
            return value.substring(0, 3);
        }
        return value.substring(0, 2);
    }


    public boolean belongsToCity(String cityId) {
        String expectedPrefix = getCityPrefix(cityId);
        return value.startsWith(expectedPrefix);
    }

    public StopCode nextInSequence() {
        String prefix = getCityPrefix();
        return new StopCode(prefix + "999");
    }


    private static void validateFormat(String value) {
//        if (!VALID_CODE_PATTERN.matcher(value).matches()) {
//            throw new IllegalArgumentException(
//                    "Invalid stop code format: " + value +
//                            ". Expected format: {PREFIX}{3DIGITS} or {PREFIX}{3DIGITS}{2DIGITS}"
//            );
//        }
    }

    private static String getCityPrefix(String cityId) {
        return CITY_PREFIXES.getOrDefault(cityId, DEFAULT_PREFIX);
    }


    @Override
    public String toString() {
        return "StopCode{" +
                "value='" + value + '\'' +
                '}';
    }
}
