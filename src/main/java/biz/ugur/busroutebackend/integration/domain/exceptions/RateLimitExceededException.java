package biz.ugur.busroutebackend.integration.domain.exceptions;


import lombok.Getter;

@Getter
public class RateLimitExceededException extends ExternalServiceException {

    private final int limit;
    private final int current;

    public RateLimitExceededException(String serviceName, int limit, int current) {
        super(String.format("Rate limit exceeded for service '%s': %d/%d requests per minute",
            serviceName, current, limit));
        this.limit = limit;
        this.current = current;
    }

}
