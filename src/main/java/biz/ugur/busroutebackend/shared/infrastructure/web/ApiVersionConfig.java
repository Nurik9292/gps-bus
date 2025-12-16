package biz.ugur.busroutebackend.shared.infrastructure.web;

public class ApiVersionConfig {

    public static final String API_V1 = "/api/v1";

    public static final String MOBILE = "/mobile";
    public static final String ADMIN = "/admin";
    public static final String CLIENT = "/client";
    public static final String ROUTING = "/trip-planning";

    public static final String AUTH = "/auth";
    public static final String ROUTES = "/routes";
    public static final String STOPS = "/stops";
    public static final String VEHICLES = "/vehicles";
    public static final String BANNERS = "/banners";
    public static final String CITIES = "/cities";
    public static final String USERS = "/users";
    public static final String FAVORITES = "/favorites";
    public static final String AVATARS = "/avatars";
    public static final String Banners = "/banners";
    public static final String NOTIFICATIONS = "/notifications";
    public static final String EXTERNAL_SERVICES = "/external-services";
    public static final String SHIFT_ASSIGNMENTS = "/shift-assignments";
    public static final String ROUTE_ALTERNATIVES = "/route-alternatives";
    public static final String LEGAL = "/legal";
    public static final String DASHBOARD = "/dashboard";

    public static final String V1_MOBILE = API_V1 + MOBILE;
    public static final String V1_LEGAL = API_V1 + LEGAL;
    public static final String V1_ADMIN = API_V1 + ADMIN;
    public static final String V1_CLIENT = API_V1 + CLIENT;
    public static final String V1_ROUTING = API_V1 + ROUTING;
    public static final String V1_ROUTES = API_V1 + ROUTES;
    public static final String V1_STOPS = API_V1 + STOPS;
    public static final String V1_AVATARS = API_V1 + AVATARS;
    public static final String V1_BANNERS = API_V1 + BANNERS;

    public static final String V1_MOBILE_BANNERS = V1_MOBILE + BANNERS;
    public static final String V1_MOBILE_NOTIFICATIONS = V1_MOBILE + NOTIFICATIONS;
    public static final String V1_MOBILE_ROUTES = V1_MOBILE + ROUTES;
    public static final String V1_MOBILE_STOPS = V1_MOBILE + STOPS;
    public static final String V1_MOBILE_VEHICLES = V1_MOBILE + VEHICLES;
    public static final String V1_MOBILE_DASHBOARD = V1_MOBILE + DASHBOARD;


    public static final String V1_ADMIN_AUTH =  V1_ADMIN + AUTH;
    public static final String V1_ADMIN_BANNERS = V1_ADMIN + BANNERS;
    public static final String V1_ADMIN_NOTIFICATIONS = V1_ADMIN + NOTIFICATIONS;
    public static final String V1_ADMIN_CITIES = V1_ADMIN + CITIES;
    public static final String V1_ADMIN_ROUTES = V1_ADMIN + ROUTES;
    public static final String V1_ADMIN_STOPS = V1_ADMIN + STOPS;
    public static final String V1_ADMIN_USERS = V1_ADMIN + USERS;
    public static final String V1_ADMIN_EXTERNAL_SERVICES = V1_ADMIN + EXTERNAL_SERVICES;
    public static final String V1_ADMIN_SHIFT_ASSIGNMENTS = V1_ADMIN + SHIFT_ASSIGNMENTS;
    public static final String V1_ADMIN_VEHICLES = V1_ADMIN + VEHICLES;
    public static final String V1_ADMIN_ROUTE_ALTERNATIVES = V1_ADMIN + ROUTE_ALTERNATIVES;
    public static final String V1_ADMIN_DASHBOARD = V1_ADMIN + DASHBOARD;


    public static final String V1_CLIENT_AUTH = V1_CLIENT + AUTH;
    public static final String V1_CLIENT_FAVORITES = V1_CLIENT + FAVORITES;
}
