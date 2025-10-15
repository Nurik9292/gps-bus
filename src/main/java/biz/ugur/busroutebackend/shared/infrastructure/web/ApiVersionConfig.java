package biz.ugur.busroutebackend.shared.infrastructure.web;

public class ApiVersionConfig {

    public static final String API_V1 = "/api/v1";

    public static final String MOBILE = "/mobile";
    public static final String ADMIN = "/admin";
    public static final String CLIENT = "/client";
    public static final String ROUTING = "/trip-planning";
    public static final String TRANSPORT = "/transport";

    public static final String V1_MOBILE = API_V1 + MOBILE;
    public static final String V1_ADMIN = API_V1 + ADMIN;
    public static final String V1_CLIENT = API_V1 + CLIENT;
    public static final String V1_ROUTING = API_V1 + ROUTING;
    public static final String V1_TRANSPORT = API_V1 + TRANSPORT;
}
