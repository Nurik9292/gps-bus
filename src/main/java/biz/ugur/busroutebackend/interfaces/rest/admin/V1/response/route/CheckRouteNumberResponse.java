package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.route;

public record CheckRouteNumberResponse(Boolean available) {

    public static CheckRouteNumberResponse of(Boolean available) {
        return new CheckRouteNumberResponse(available);
    }
}
