package biz.ugur.busroutebackend.shared.infrastructure.exception;

import biz.ugur.busroutebackend.shared.domain.exception.AbstractDomainException;
import biz.ugur.busroutebackend.shared.domain.exception.DomainException;
import org.springframework.http.HttpStatus;


public class HttpStatusMapper {

    private HttpStatusMapper() {

    }


    public static HttpStatus mapFromException(AbstractDomainException exception) {
        String errorCode = exception.getErrorCode();

        if (errorCode == null || errorCode.isBlank()) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

        return switch (errorCode) {
            // 404 Not Found
            case String s when s.endsWith(".NOT_FOUND") -> HttpStatus.NOT_FOUND;

            // 409 Conflict
            case String s when s.endsWith(".ALREADY_EXISTS") -> HttpStatus.CONFLICT;
            case String s when s.endsWith(".CONCURRENCY_ERROR") -> HttpStatus.CONFLICT;
            case String s when s.endsWith(".CONFLICT") -> HttpStatus.CONFLICT;

            // 400 Bad Request
            case String s when s.endsWith(".VALIDATION_ERROR") -> HttpStatus.BAD_REQUEST;
            case String s when s.endsWith(".INVALID_REQUEST") -> HttpStatus.BAD_REQUEST;
            case String s when s.endsWith(".INVALID_ARGUMENT") -> HttpStatus.BAD_REQUEST;
            case String s when s.endsWith(".BAD_REQUEST") -> HttpStatus.BAD_REQUEST;

            // 401 Unauthorized
            case String s when s.endsWith(".AUTHENTICATION_ERROR") -> HttpStatus.UNAUTHORIZED;
            case String s when s.endsWith(".TOKEN_ERROR") -> HttpStatus.UNAUTHORIZED;
            case String s when s.endsWith(".UNAUTHORIZED") -> HttpStatus.UNAUTHORIZED;
            case String s when s.endsWith(".INVALID_CREDENTIALS") -> HttpStatus.UNAUTHORIZED;

            // 403 Forbidden
            case String s when s.endsWith(".ACCESS_DENIED") -> HttpStatus.FORBIDDEN;
            case String s when s.endsWith(".FORBIDDEN") -> HttpStatus.FORBIDDEN;
            case String s when s.endsWith(".INSUFFICIENT_PERMISSIONS") -> HttpStatus.FORBIDDEN;

            // 422 Unprocessable Entity
            case String s when s.endsWith(".BUSINESS_RULE_VIOLATION") -> HttpStatus.UNPROCESSABLE_ENTITY;

            // 503 Service Unavailable
            case String s when s.endsWith(".SERVICE_UNAVAILABLE") -> HttpStatus.SERVICE_UNAVAILABLE;
            case String s when s.endsWith(".EXTERNAL_SERVICE_ERROR") -> HttpStatus.SERVICE_UNAVAILABLE;

            // 500 Internal Server Error (default)
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    public static HttpStatus mapFromErrorCode(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

        return switch (errorCode) {
            case String s when s.endsWith(".NOT_FOUND") -> HttpStatus.NOT_FOUND;
            case String s when s.endsWith(".ALREADY_EXISTS") -> HttpStatus.CONFLICT;
            case String s when s.endsWith(".CONCURRENCY_ERROR") -> HttpStatus.CONFLICT;
            case String s when s.endsWith(".VALIDATION_ERROR") -> HttpStatus.BAD_REQUEST;
            case String s when s.endsWith(".AUTHENTICATION_ERROR") -> HttpStatus.UNAUTHORIZED;
            case String s when s.endsWith(".TOKEN_ERROR") -> HttpStatus.UNAUTHORIZED;
            case String s when s.endsWith(".ACCESS_DENIED") -> HttpStatus.FORBIDDEN;
            case String s when s.endsWith(".FORBIDDEN") -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    public static HttpStatus mapFromSeverity(DomainException.Severity severity) {
        if (severity == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

        return switch (severity) {
            case CRITICAL -> HttpStatus.INTERNAL_SERVER_ERROR;
            case ERROR -> HttpStatus.BAD_REQUEST;
            case WARNING, INFO -> HttpStatus.OK;
        };
    }


    public static boolean isClientError(HttpStatus status) {
        return status.is4xxClientError();
    }

    public static boolean isServerError(HttpStatus status) {
        return status.is5xxServerError();
    }
}
