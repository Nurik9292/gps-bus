package biz.ugur.busroutebackend.transport.domain.exceptions;

public class AssignmentValidationException extends RuntimeException {

    public AssignmentValidationException(String message) {
        super(message);
    }

    public AssignmentValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
