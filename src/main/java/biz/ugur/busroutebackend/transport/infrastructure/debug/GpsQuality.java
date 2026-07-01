package biz.ugur.busroutebackend.transport.infrastructure.debug;

public record GpsQuality(Double hdop, Integer satellites, Double accuracy) {

    public static final GpsQuality UNKNOWN = new GpsQuality(null, null, null);
}
