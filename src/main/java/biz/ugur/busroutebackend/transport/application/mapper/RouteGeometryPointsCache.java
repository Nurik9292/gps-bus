package biz.ugur.busroutebackend.transport.application.mapper;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteGeometry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class RouteGeometryPointsCache {

    private record CachedPoints(Object version, List<Coordinates> points) {}

    private final ConcurrentHashMap<String, CachedPoints> cache = new ConcurrentHashMap<>();

    public List<Coordinates> forwardPoints(BusRoute route) {
        return pointsByVersion(route.getId().getValue() + ":0", versionOf(route), () -> pointsOf(route.getForwardGeometry()));
    }

    public List<Coordinates> backwardPoints(BusRoute route) {
        return pointsByVersion(route.getId().getValue() + ":1", versionOf(route), () -> pointsOf(route.getBackwardGeometry()));
    }

    private List<Coordinates> pointsByVersion(String key, Object version, Supplier<List<Coordinates>> parser) {
        CachedPoints existing = cache.get(key);
        if (existing != null && Objects.equals(existing.version(), version)) {
            return existing.points();
        }
        List<Coordinates> parsed = parser.get();
        cache.put(key, new CachedPoints(version, parsed));
        return parsed;
    }

    private static Object versionOf(BusRoute route) {
        return route.getVersion() != null ? route.getVersion() : route.getUpdatedAt();
    }

    private static List<Coordinates> pointsOf(RouteGeometry geometry) {
        return geometry != null ? geometry.getPoints() : List.of();
    }
}
