package biz.ugur.busroutebackend.routing.domain.constants;

public final class TransferRouteConstants {

    private TransferRouteConstants() {}

    public static final int ONE_TRANSFER_MAX_TOTAL_TIME_MINUTES = 100;
    public static final int ONE_TRANSFER_MAX_WAIT_TIME_MINUTES = 30;

    public static final int TWO_TRANSFER_MAX_TOTAL_TIME_MINUTES = 150;
    public static final int TWO_TRANSFER_MAX_WAIT_TIME_MINUTES = 25;

    public static final int MIN_SEGMENT_TRAVEL_TIME_MINUTES = 1;

    public static final int DIRECT_ROUTE_MAX_WALKING_MINUTES = 15;
    public static final int ONE_TRANSFER_MAX_WALKING_MINUTES = 15;
    public static final int TWO_TRANSFER_MAX_WALKING_MINUTES = 18;
}
