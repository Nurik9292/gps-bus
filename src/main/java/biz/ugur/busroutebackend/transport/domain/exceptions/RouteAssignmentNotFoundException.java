package biz.ugur.busroutebackend.transport.domain.exceptions;

public class RouteAssignmentNotFoundException extends RuntimeException {

    public RouteAssignmentNotFoundException(String message) {
        super(message);
    }

    public static RouteAssignmentNotFoundException byId(String id) {
        return new RouteAssignmentNotFoundException("Route assignment not found: " + id);
    }
}
