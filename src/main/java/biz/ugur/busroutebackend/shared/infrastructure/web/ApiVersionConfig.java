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
    public static final String ROUTE_ASSIGNMENTS = "/route-assignments";
    public static final String ROUTE_ALTERNATIVES = "/route-alternatives";
    public static final String LEGAL = "/legal";
    public static final String DASHBOARD = "/dashboard";
    public static final String INTEGRATION = "/integration";
    public static final String CLIENTS = "/clients";

    public static final String V1_MOBILE = API_V1 + MOBILE;
    public static final String V1_LEGAL = API_V1 + LEGAL;
    public static final String V1_ADMIN = API_V1 + ADMIN;
    public static final String V1_CLIENT = API_V1 + CLIENT;
    public static final String V1_ROUTING = API_V1 + ROUTING;
    public static final String V1_ROUTES = API_V1 + ROUTES;
    public static final String V1_STOPS = API_V1 + STOPS;

    public static final String V1_MOBILE_BANNERS = V1_MOBILE + BANNERS;
    public static final String V1_MOBILE_CITIES = V1_MOBILE + CITIES;
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
    public static final String V1_ADMIN_ROUTE_ASSIGNMENTS = V1_ADMIN + ROUTES + "/assignments";
    public static final String V1_ADMIN_VEHICLES = V1_ADMIN + VEHICLES;
    public static final String V1_ADMIN_ROUTE_ALTERNATIVES = V1_ADMIN + ROUTE_ALTERNATIVES;
    public static final String V1_ADMIN_DASHBOARD             = V1_ADMIN + DASHBOARD;
    public static final String V1_ADMIN_DASHBOARD_ADVERTISING = V1_ADMIN_DASHBOARD + "/advertising-overview";
    public static final String ROUTING_ADMIN = "/routing";
    public static final String V1_ADMIN_ROUTING = V1_ADMIN + ROUTING_ADMIN;


    public static final String V1_CLIENT_AUTH = V1_CLIENT + AUTH;
    public static final String V1_CLIENT_FAVORITES = V1_CLIENT + FAVORITES;

    public static final String SUGGESTIONS = "/suggestions";
    public static final String V1_CLIENT_SUGGESTIONS = V1_CLIENT + SUGGESTIONS;
    public static final String V1_ADMIN_SUGGESTIONS = V1_ADMIN + SUGGESTIONS;

    public static final String V1_INTEGRATION = API_V1 + INTEGRATION;
    public static final String V1_INTEGRATION_CLIENTS = V1_INTEGRATION + CLIENTS;

    public static final String SUBSCRIPTIONS = "/subscriptions";
    public static final String V1_CLIENT_SUBSCRIPTIONS = V1_CLIENT + SUBSCRIPTIONS;

    public static final String PLACES = "/places";
    public static final String STREETS = "/streets";
    public static final String V1_PLACES = API_V1 + PLACES;
    public static final String V1_MOBILE_PLACES = V1_MOBILE + PLACES;
    public static final String V1_ADMIN_PLACES = V1_ADMIN + PLACES;
    public static final String V1_ADMIN_STREETS = V1_ADMIN + STREETS;

    public static final String COMPLAINTS = "/complaints";
    public static final String V1_CLIENT_COMPLAINTS = V1_CLIENT + COMPLAINTS;
    public static final String V1_ADMIN_COMPLAINTS = V1_ADMIN + COMPLAINTS;

    public static final String ANALYTICS = "/analytics";
    public static final String V1_ADMIN_ANALYTICS = V1_ADMIN + ANALYTICS;

    public static final String BUSINESSES = "/businesses";
    public static final String AD_TARIFFS = "/ad-tariffs";
    public static final String AD_PLACEMENTS = "/ad-placements";
    public static final String ADS = "/ads";

    public static final String V1_ADMIN_BUSINESSES     = V1_ADMIN + BUSINESSES;
    public static final String V1_ADMIN_AD_TARIFFS     = V1_ADMIN + AD_TARIFFS;
    public static final String V1_ADMIN_AD_PLACEMENTS  = V1_ADMIN + AD_PLACEMENTS;
    public static final String V1_ADMIN_AD_PLACEMENT_PAYMENT_CALLBACK = V1_ADMIN_AD_PLACEMENTS + "/{placementId}/payment-callback";
    public static final String V1_ADMIN_AD_PLACEMENT_ANALYTICS_TREND = V1_ADMIN_AD_PLACEMENTS + "/{placementId}/analytics/trend";
    public static final String V1_ADMIN_AD_PLACEMENT_SALES_REPORT     = V1_ADMIN_AD_PLACEMENTS + "/sales-report";

    public static final String V1_MOBILE_ADS              = V1_MOBILE + ADS;
    public static final String V1_MOBILE_AD_DETAIL_VIEW   = V1_MOBILE_ADS + "/{placementId}/detail-view";
    public static final String V1_MOBILE_AD_TARIFFS       = V1_MOBILE + AD_TARIFFS;

    public static final String PAYMENTS = "/payments";
    public static final String V1_ADMIN_PAYMENTS  = V1_ADMIN + PAYMENTS;
    public static final String V1_PAYMENTS_RETURN = API_V1 + PAYMENTS + "/return";

    public static final String V1_ADMIN_GPS_RECORDER = V1_ADMIN + "/debug/gps-recorder";

    public static final String DIAGNOSTICS = "/diagnostics";
    public static final String V1_DIAGNOSTICS = API_V1 + DIAGNOSTICS;
    public static final String V1_DIAGNOSTICS_VEHICLE_SNAPSHOT = V1_DIAGNOSTICS + "/vehicle-snapshot";
    public static final String V1_DIAGNOSTICS_GPS_TRAIL = V1_DIAGNOSTICS + "/gps-trail";
}
