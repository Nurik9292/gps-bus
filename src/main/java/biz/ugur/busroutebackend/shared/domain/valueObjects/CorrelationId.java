package biz.ugur.busroutebackend.shared.domain.valueObjects;

import java.util.UUID;
import java.util.regex.Pattern;


public record CorrelationId(String value)  {

    private static final Pattern VALID_PATTERN = Pattern.compile("^[A-Z]{2,10}-[0-9a-f]{8}(-[0-9a-f]{4})?$");
    private static final String DEFAULT_PREFIX = "REQ";

    public CorrelationId {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Correlation ID cannot be null or empty");
        }
        if (!VALID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid correlation ID format: " + value);
        }
    }

    public static CorrelationId generate() {
        return generate(DEFAULT_PREFIX);
    }

    public static CorrelationId generate(String prefix) {
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return new CorrelationId(prefix + "-" + uuid);
    }

    public static CorrelationId forAdmin() {
        return generate("ADMIN");
    }

    public static CorrelationId forTransport() {
        return generate("TRANS");
    }

    public static CorrelationId forRouting() {
        return generate("ROUTING");
    }

    public static CorrelationId forClient() {
        return generate("CLIENT");
    }

    public static CorrelationId forMobile() {
        return generate("MOBILE");
    }



    public static CorrelationId fromString(String value) {
        return new CorrelationId(value);
    }

}
