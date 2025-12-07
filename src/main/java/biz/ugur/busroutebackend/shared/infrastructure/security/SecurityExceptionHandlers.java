package biz.ugur.busroutebackend.shared.infrastructure.security;

import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public final class SecurityExceptionHandlers {

    private static final ObjectMapper objectMapper = createObjectMapper();

    private SecurityExceptionHandlers() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    public static ServerAuthenticationEntryPoint authenticationEntryPoint() {
        return (exchange, ex) -> {
            String path = exchange.getRequest().getPath().value();
            String correlationId = CorrelationId.generate().value();

            log.warn("Authentication required for path: {} - CorrelationId: {} - {}",
                    path, correlationId, ex.getMessage());

            ServerHttpResponse response = exchange.getResponse();

            if (response.isCommitted()) {
                log.warn("Response already committed for path: {}, cannot send authentication error", path);
                return reactor.core.publisher.Mono.empty();
            }

            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().add("Content-Type", "application/json");

            Map<String, Object> errorBody = createErrorResponse(
                    HttpStatus.UNAUTHORIZED.value(),
                    "AUTH.UNAUTHORIZED",
                    "Authentication required. Please provide valid credentials.",
                    correlationId,
                    path,
                    "WARNING"
            );

            String body = toJson(errorBody);
            DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(reactor.core.publisher.Mono.just(buffer));
        };
    }

    public static ServerAccessDeniedHandler accessDeniedHandler() {
        return (exchange, denied) -> {
            String path = exchange.getRequest().getPath().value();
            String correlationId = CorrelationId.generate().value();

            log.warn("Access denied for path: {} - CorrelationId: {} - {}",
                    path, correlationId, denied.getMessage());

            ServerHttpResponse response = exchange.getResponse();

            if (response.isCommitted()) {
                log.warn("Response already committed for path: {}, cannot send access denied error", path);
                return reactor.core.publisher.Mono.empty();
            }

            response.setStatusCode(HttpStatus.FORBIDDEN);
            response.getHeaders().add("Content-Type", "application/json");

            Map<String, Object> errorBody = createErrorResponse(
                    HttpStatus.FORBIDDEN.value(),
                    "AUTH.ACCESS_DENIED",
                    "Insufficient permissions. You do not have access to this resource.",
                    correlationId,
                    path,
                    "WARNING"
            );

            String body = toJson(errorBody);
            DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(reactor.core.publisher.Mono.just(buffer));
        };
    }

    private static Map<String, Object> createErrorResponse(
            int status,
            String errorCode,
            String message,
            String correlationId,
            String path,
            String severity
    ) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", status);
        response.put("errorCode", errorCode);
        response.put("message", message);
        response.put("correlationId", correlationId);
        response.put("timestamp", LocalDateTime.now());
        response.put("severity", severity);
        response.put("boundedContext", extractBoundedContext(errorCode));
        response.put("path", path);
        return response;
    }

    private static String extractBoundedContext(String errorCode) {
        if (errorCode != null && errorCode.contains(".")) {
            return errorCode.substring(0, errorCode.indexOf(".")).toLowerCase();
        }
        return "unknown";
    }

    private static String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error response", e);
            return String.format(
                    "{\"status\":%d,\"errorCode\":\"%s\",\"message\":\"%s\"}",
                    map.get("status"),
                    map.get("errorCode"),
                    map.get("message")
            );
        }
    }
}
