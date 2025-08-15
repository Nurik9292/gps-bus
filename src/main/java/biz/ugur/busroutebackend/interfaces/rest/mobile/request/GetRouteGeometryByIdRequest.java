package biz.ugur.busroutebackend.interfaces.rest.mobile.request;

public record GetRouteGeometryByIdRequest(String routeId, Integer direction) {}