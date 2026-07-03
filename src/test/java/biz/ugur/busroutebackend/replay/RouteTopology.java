package biz.ugur.busroutebackend.replay;

public record RouteTopology(String topology, GeometryFixture first, GeometryFixture second) {

    public static RouteTopology of(GeometryFixture g) {
        if (g.isLoop()) {
            return new RouteTopology(GeometryFixture.TOPOLOGY_LOOP, g, null);
        }
        if (!GeometryFixture.TOPOLOGY_THERE_AND_BACK.equals(g.topology())) {
            System.out.printf("topology=%s UNIMPLEMENTED (branching вне scope v1) — маршрут %s%n",
                    g.topology(), g.routeNumber());
            throw new UnsupportedOperationException("topology " + g.topology() + " вне scope v1");
        }
        return new RouteTopology(GeometryFixture.TOPOLOGY_THERE_AND_BACK, g, null);
    }

    public static RouteTopology thereAndBack(GeometryFixture dir0, GeometryFixture dir1) {
        return new RouteTopology(GeometryFixture.TOPOLOGY_THERE_AND_BACK, dir0, dir1);
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
}
