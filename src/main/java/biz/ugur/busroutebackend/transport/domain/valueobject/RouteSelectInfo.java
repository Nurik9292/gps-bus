package biz.ugur.busroutebackend.transport.domain.valueobject;

public record RouteSelectInfo(String id, String routeNumber, String routeName,
                              String cityId, String cityName) {
}
