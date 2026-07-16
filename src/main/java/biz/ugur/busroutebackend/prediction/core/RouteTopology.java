package biz.ugur.busroutebackend.prediction.core;

import java.util.ArrayList;
import java.util.List;

public record RouteTopology(String topology, RouteLine first, RouteLine second,
                            List<RouteLine> variants, CityZone cityZone) {

    public record CityZone(double deepLat, double deepLon,
                           double plateauLat, double plateauLon) {}

    public RouteTopology {
        variants = variants == null ? List.of() : List.copyOf(variants);
    }

    public static RouteTopology of(RouteLine g) {
        if (g.isLoop()) {
            return new RouteTopology(RouteLine.TOPOLOGY_LOOP, g, null, List.of(), null);
        }
        if (!RouteLine.TOPOLOGY_THERE_AND_BACK.equals(g.topology())) {
            System.out.printf("topology=%s UNIMPLEMENTED (branching вне scope v1) — маршрут %s%n",
                    g.topology(), g.routeNumber());
            throw new UnsupportedOperationException("topology " + g.topology() + " вне scope v1");
        }
        return new RouteTopology(RouteLine.TOPOLOGY_THERE_AND_BACK, g, null, List.of(), null);
    }

    public static RouteTopology thereAndBack(RouteLine dir0, RouteLine dir1) {
        return new RouteTopology(RouteLine.TOPOLOGY_THERE_AND_BACK, dir0, dir1, List.of(), null);
    }

    public RouteTopology withVariants(List<RouteLine> extraVariants) {
        return new RouteTopology(topology, first, second, extraVariants, cityZone);
    }

    public RouteTopology withCityZone(CityZone zone) {
        return new RouteTopology(topology, first, second, variants, zone);
    }

    public boolean isLoop() {
        return RouteLine.TOPOLOGY_LOOP.equals(topology);
    }

    public RouteLine geom(int direction) {
        if (second != null && second.direction() == direction) return second;
        return first;
    }

    public RouteLine opposite(int direction) {
        if (isLoop() || second == null) return null;
        return geom(direction) == first ? second : first;
    }

    public boolean hasOpposite(int direction) {
        return opposite(direction) != null;
    }

    public List<RouteLine> allGeometries() {
        List<RouteLine> out = new ArrayList<>();
        out.add(first);
        if (second != null) out.add(second);
        out.addAll(variants);
        return out;
    }
}
