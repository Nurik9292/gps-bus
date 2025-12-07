package biz.ugur.busroutebackend.integration.domain.exceptions;

import lombok.Getter;

@Getter
public class RateLimitExceededException extends ExternalServiceException {

    private final String serviceName;
    private final int limit;
    private final int current;

    public RateLimitExceededException(String serviceName, int limit, int current) {
        super("RATE_LIMIT_EXCEEDED",
              String.format("Rate limit exceeded for service '%s': %d/%d requests per minute", serviceName, current, limit),
              Severity.WARNING);
        this.serviceName = serviceName;
        this.limit = limit;
        this.current = current;
    }

    public boolean isRetryable() {
        return true;
    }

    public static RateLimitExceededException forService(String serviceName, int limit, int current) {
        return new RateLimitExceededException(serviceName, limit, current);
    }
}
