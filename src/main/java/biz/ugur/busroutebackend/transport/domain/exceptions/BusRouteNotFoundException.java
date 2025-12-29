package biz.ugur.busroutebackend.transport.domain.exceptions;


public class BusRouteNotFoundException extends RuntimeException {

    public BusRouteNotFoundException(String message) {
        super(message);
    }

    public static BusRouteNotFoundException byId(String id) {
        return new BusRouteNotFoundException("Bus route not found: " + id);
    }
}
