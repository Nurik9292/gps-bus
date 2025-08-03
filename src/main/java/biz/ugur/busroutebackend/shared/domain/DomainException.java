package biz.ugur.busroutebackend.shared.domain;

import java.time.Instant;

public interface DomainException {
    String getErrorCode();
    String getMessage();
    Instant getTimestamp();
    CorrelationId getCorrelationId();
    Severity getSeverity();

    enum Severity {
        INFO("Info"),
        WARNING("Warning"),
        ERROR("Error"),
        CRITICAL("Critical");

        private final String value;

        Severity(String value) {
            this.value = value;
        }

        public String getDisplayName() {
            return value;
        }
    }
}