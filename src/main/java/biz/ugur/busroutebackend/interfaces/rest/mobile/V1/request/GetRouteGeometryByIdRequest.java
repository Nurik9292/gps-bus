package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.request;

public record GetRouteGeometryByIdRequest(String routeId, Integer direction) {}