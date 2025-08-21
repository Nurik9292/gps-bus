package biz.ugur.busroutebackend.shared.infrastructure.correlation;

import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

@Slf4j
@Component
public class CorrelationIdGenerator {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String REQUEST_ID_HEADER = "X-Request-ID";

    public CorrelationId extractOrGenerate(ServerWebExchange exchange) {
        String existingCorrelationId = exchange.getRequest()
                .getHeaders()
                .getFirst(CORRELATION_ID_HEADER);

        if (existingCorrelationId != null && !existingCorrelationId.trim().isEmpty()) {
            try {
                return CorrelationId.fromString(existingCorrelationId);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid correlation ID in header: {}, generating new one", existingCorrelationId);
            }
        }

        String requestId = exchange.getRequest()
                .getHeaders()
                .getFirst(REQUEST_ID_HEADER);

        if (requestId != null && !requestId.trim().isEmpty()) {
            try {
                return CorrelationId.fromString("REQ-" + requestId.substring(0, Math.min(8, requestId.length())));
            } catch (Exception e) {
                log.debug("Could not use request ID: {}", requestId);
            }
        }

        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/api/v1/admin/")) {
            return CorrelationId.forAdmin();
        } else if (path.startsWith("/api/v1/transport/") || path.startsWith("/ws/vehicle-positions")) {
            return CorrelationId.forTransport();
        } else if (path.startsWith("/api/v1/trip-planning/")) {
            return CorrelationId.forRouting();
        } else if (path.startsWith("/api/v1/routes/")) {
            return CorrelationId.forRoutes();
        }  else if (path.startsWith("/api/v1/client/")) {
            return CorrelationId.forClient();
        }

        return CorrelationId.generate();
    }


    public void addToResponse(ServerWebExchange exchange, CorrelationId correlationId) {
        exchange.getResponse().getHeaders().add(CORRELATION_ID_HEADER, correlationId.value());
    }
}
