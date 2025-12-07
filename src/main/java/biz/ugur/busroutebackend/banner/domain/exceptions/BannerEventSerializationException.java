package biz.ugur.busroutebackend.banner.domain.exceptions;

import lombok.Getter;

@Getter
public class BannerEventSerializationException extends BannerDomainException {

    private final String eventType;
    private final String operation;

    public BannerEventSerializationException(String message) {
        super("EVENT_SERIALIZATION_ERROR", message, Severity.ERROR);
        this.eventType = null;
        this.operation = null;
    }

    public BannerEventSerializationException(String message, Throwable cause) {
        super("EVENT_SERIALIZATION_ERROR", message, Severity.ERROR);
        this.eventType = null;
        this.operation = null;
        initCause(cause);
    }

    public BannerEventSerializationException(String eventType, String operation, String message) {
        super("EVENT_SERIALIZATION_ERROR",
              String.format("Event serialization failed for '%s' during %s: %s", eventType, operation, message),
              Severity.ERROR);
        this.eventType = eventType;
        this.operation = operation;
    }

    public static BannerEventSerializationException serializationFailed(String eventType, Throwable cause) {
        BannerEventSerializationException ex = new BannerEventSerializationException(
            eventType, "serialization", cause.getMessage());
        ex.initCause(cause);
        return ex;
    }

    public static BannerEventSerializationException deserializationFailed(String eventType, Throwable cause) {
        BannerEventSerializationException ex = new BannerEventSerializationException(
            eventType, "deserialization", cause.getMessage());
        ex.initCause(cause);
        return ex;
    }
}
