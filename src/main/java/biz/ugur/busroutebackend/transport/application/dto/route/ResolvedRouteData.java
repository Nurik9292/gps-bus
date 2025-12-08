package biz.ugur.busroutebackend.transport.application.dto.route;


public record ResolvedRouteData(
        RouteData routeData,
        boolean isAlternative,
        String originalRouteId,
        String originalRouteNumber
) {


    public static ResolvedRouteData primary(RouteData routeData) {
        return new ResolvedRouteData(routeData, false, null, null);
    }


    public static ResolvedRouteData alternative(
            RouteData alternativeRouteData,
            String originalRouteId,
            String originalRouteNumber) {
        return new ResolvedRouteData(alternativeRouteData, true, originalRouteId, originalRouteNumber);
    }


    public boolean hasActiveRoute() {
        return routeData != null && routeData.isActiveRoute();
    }
}
