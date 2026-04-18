package biz.ugur.busroutebackend.shared.infrastructure.exception.handlers;

import biz.ugur.busroutebackend.shared.infrastructure.exception.ErrorResponse;
import biz.ugur.busroutebackend.shared.infrastructure.exception.ErrorResponseFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class DatabaseExceptionHandler {

    private final ErrorResponseFactory errorResponseFactory;

    @ExceptionHandler(DuplicateKeyException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleDuplicateKeyException(
            DuplicateKeyException ex,
            ServerWebExchange exchange
    ) {
        String constraintName = extractConstraintName(ex.getMessage());
        String userMessage = mapConstraintToMessage(constraintName, ex.getMessage());

        log.warn("Duplicate key violation - Path: {} - Constraint: {} - Message: {}",
                exchange.getRequest().getPath().value(), constraintName, userMessage);

        Map<String, List<String>> fieldErrors = mapConstraintToFieldErrors(constraintName);

        ErrorResponse errorResponse = errorResponseFactory.fromValidationError(
                HttpStatus.CONFLICT,
                "DUPLICATE_KEY",
                userMessage,
                exchange,
                fieldErrors
        );

        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleDataIntegrityViolationException(
            DataIntegrityViolationException ex,
            ServerWebExchange exchange
    ) {
        String constraintName = extractConstraintName(ex.getMessage());
        String userMessage = mapConstraintToMessage(constraintName, ex.getMessage());

        log.warn("Data integrity violation - Path: {} - Constraint: {} - Message: {}",
                exchange.getRequest().getPath().value(), constraintName, userMessage);

        ErrorResponse errorResponse = errorResponseFactory.fromGenericError(
                HttpStatus.CONFLICT,
                "DATA_INTEGRITY_VIOLATION",
                userMessage,
                exchange
        );

        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse));
    }

    private String extractConstraintName(String message) {
        if (message == null) return "unknown";

        if (message.contains("violates unique constraint")) {
            int start = message.indexOf("\"");
            int end = message.indexOf("\"", start + 1);
            if (start != -1 && end != -1) {
                return message.substring(start + 1, end);
            }
        }

        if (message.contains("violates foreign key constraint")) {
            int start = message.indexOf("\"");
            int end = message.indexOf("\"", start + 1);
            if (start != -1 && end != -1) {
                return message.substring(start + 1, end);
            }
        }

        return "unknown";
    }

    private String mapConstraintToMessage(String constraintName, String originalMessage) {
        return switch (constraintName) {
            case "bus_routes_route_number_key" -> "Маршрут с таким номером уже существует";
            case "bus_routes_route_name_key" -> "Маршрут с таким названием уже существует";
            case "bus_stops_name_key", "bus_stops_stop_name_key" -> "Остановка с таким названием уже существует";
            case "admins_username_key" -> "Администратор с таким логином уже существует";
            case "clients_email_key" -> "Пользователь с таким email уже существует";
            case "clients_phone_key" -> "Пользователь с таким телефоном уже существует";
            case "vehicles_gov_number_key" -> "Транспорт с таким гос. номером уже существует";
            case "banners_title_key" -> "Баннер с таким заголовком уже существует";
            case "cities_name_key" -> "Город с таким названием уже существует";
            case "external_services_name_key" -> "Внешний сервис с таким названием уже существует";
            default -> {
                if (originalMessage != null && originalMessage.contains("duplicate key")) {
                    yield "Запись с такими данными уже существует";
                }
                yield "Нарушение целостности данных";
            }
        };
    }

    private Map<String, List<String>> mapConstraintToFieldErrors(String constraintName) {
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();

        switch (constraintName) {
            case "bus_routes_route_number_key" ->
                    fieldErrors.put("routeNumber", List.of("Маршрут с таким номером уже существует"));
            case "bus_routes_route_name_key" ->
                    fieldErrors.put("routeName", List.of("Маршрут с таким названием уже существует"));
            case "bus_stops_name_key", "bus_stops_stop_name_key" ->
                    fieldErrors.put("name", List.of("Остановка с таким названием уже существует"));
            case "admins_username_key" ->
                    fieldErrors.put("username", List.of("Администратор с таким логином уже существует"));
            case "clients_email_key" ->
                    fieldErrors.put("email", List.of("Пользователь с таким email уже существует"));
            case "clients_phone_key" ->
                    fieldErrors.put("phone", List.of("Пользователь с таким телефоном уже существует"));
            case "vehicles_gov_number_key" ->
                    fieldErrors.put("govNumber", List.of("Транспорт с таким гос. номером уже существует"));
            case "banners_title_key" ->
                    fieldErrors.put("title", List.of("Баннер с таким заголовком уже существует"));
            case "cities_name_key" ->
                    fieldErrors.put("name", List.of("Город с таким названием уже существует"));
            case "external_services_name_key" ->
                    fieldErrors.put("name", List.of("Внешний сервис с таким названием уже существует"));
        }

        return fieldErrors;
    }
}
