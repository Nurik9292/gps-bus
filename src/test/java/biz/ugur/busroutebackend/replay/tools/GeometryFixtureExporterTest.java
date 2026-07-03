package biz.ugur.busroutebackend.replay.tools;

import biz.ugur.busroutebackend.replay.GeometryFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class GeometryFixtureExporterTest {

    private static final Path OUT_DIR = Path.of("src/test/resources/fixtures/geometry");

    @Test
    @EnabledIfSystemProperty(named = "geometry.export", matches = "true")
    void exportFixturesFromDevDb() throws Exception {
        String url = System.getProperty("geometry.jdbc",
                "jdbc:postgresql://localhost:5432/bus_route_db");
        String user = System.getProperty("geometry.user", "bus_route_user");
        String pass = System.getProperty("geometry.pass", System.getenv("DB_PASSWORD"));
        String routes = System.getProperty("geometry.routes", "25,10,8");

        try (Connection c = DriverManager.getConnection(url, user, pass);
             Statement st = c.createStatement()) {
            for (String route : routes.split(",")) {
                for (int dir = 0; dir <= 1; dir++) {
                    String col = dir == 0 ? "route_geometry_forward" : "route_geometry_backward";
                    String totalCol = dir == 0 ? "total_distance_forward_meters" : "total_distance_backward_meters";
                    try (ResultSet rs = st.executeQuery(
                            "SELECT " + col + ", " + totalCol
                            + ", round(ST_Length(ST_GeomFromText(" + col + ",4326)::geography)) "
                            + "FROM bus_routes WHERE route_number='" + route.trim() + "'")) {
                        if (!rs.next() || rs.getString(1) == null) continue;
                        List<double[]> pts = parseWkt(rs.getString(1));
                        GeometryFixture fx = GeometryFixture.fromPolyline(route.trim(), dir, pts);
                        Path file = OUT_DIR.resolve("route-" + route.trim() + "-dir" + dir + ".json");
                        fx.save(file);
                        System.out.printf("route %s dir %d: fixtureL=%.1f dbTotalCol=%d dbGeogL=%d -> %s%n",
                                route.trim(), dir, fx.totalMeters(), rs.getInt(2), rs.getInt(3), file);
                    }
                }
            }
        }
    }

    private static List<double[]> parseWkt(String wkt) {
        String inner = wkt.substring(wkt.indexOf('(') + 1, wkt.lastIndexOf(')'));
        List<double[]> pts = new ArrayList<>();
        for (String tok : inner.split(",")) {
            String[] p = tok.trim().split("\\s+");
            pts.add(new double[]{Double.parseDouble(p[1]), Double.parseDouble(p[0])});
        }
        return pts;
    }
}
