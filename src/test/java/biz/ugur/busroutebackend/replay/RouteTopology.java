package biz.ugur.busroutebackend.replay;

import java.util.ArrayList;
import java.util.List;

public record RouteTopology(String topology, GeometryFixture first, GeometryFixture second,
                            List<GeometryFixture> variants) {

    public RouteTopology {
        variants = variants == null ? List.of() : List.copyOf(variants);
    }

    public static RouteTopology of(GeometryFixture g) {
        if (g.isLoop()) {
            return new RouteTopology(GeometryFixture.TOPOLOGY_LOOP, g, null, List.of());
        }
        if (!GeometryFixture.TOPOLOGY_THERE_AND_BACK.equals(g.topology())) {
            System.out.printf("topology=%s UNIMPLEMENTED (branching вне scope v1) — маршрут %s%n",
                    g.topology(), g.routeNumber());
            throw new UnsupportedOperationException("topology " + g.topology() + " вне scope v1");
        }
        return new RouteTopology(GeometryFixture.TOPOLOGY_THERE_AND_BACK, g, null, List.of());
    }

    public static RouteTopology thereAndBack(GeometryFixture dir0, GeometryFixture dir1) {
        return new RouteTopology(GeometryFixture.TOPOLOGY_THERE_AND_BACK, dir0, dir1, List.of());
    }

    public RouteTopology withVariants(List<GeometryFixture> extraVariants) {
        return new RouteTopology(topology, first, second, extraVariants);
    }

    public boolean isLoop() {
        return GeometryFixture.TOPOLOGY_LOOP.equals(topology);
    }

    public GeometryFixture geom(int direction) {
        if (second != null && second.direction() == direction) return second;
        return first;
    }

    public GeometryFixture opposite(int direction) {
        if (isLoop() || second == null) return null;
        return geom(direction) == first ? second : first;
    }

    public boolean hasOpposite(int direction) {
        return opposite(direction) != null;
    }

    public List<GeometryFixture> allGeometries() {
        List<GeometryFixture> out = new ArrayList<>();
        out.add(first);
        if (second != null) out.add(second);
        out.addAll(variants);
        return out;
    }
}
