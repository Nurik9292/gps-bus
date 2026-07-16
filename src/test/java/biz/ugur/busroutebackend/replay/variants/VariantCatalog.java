package biz.ugur.busroutebackend.replay.variants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class VariantCatalog {

    public record VariantSpec(String route, String variantId, double lengthKm, String source) {}

    public record RouteVariants(String route, String name, double totalKm,
                                VariantSpec halfA, VariantSpec halfB) {

        public double closureErrorShare() {
            if (totalKm <= 0) return Double.NaN;
            return Math.abs(halfA.lengthKm() + halfB.lengthKm() - totalKm) / totalKm;
        }
    }

    private final Map<String, RouteVariants> byRoute = new TreeMap<>();

    public static VariantCatalog loadClasspath(String resource) {
        VariantCatalog catalog = new VariantCatalog();
        try (InputStream in = VariantCatalog.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalArgumentException("no resource " + resource);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String header = reader.readLine();
            if (header == null) throw new IllegalStateException("empty catalog " + resource);
            List<String> cols = List.of(header.split(",", -1));
            int iRoute = cols.indexOf("route");
            int iName = cols.indexOf("name");
            int iTotal = cols.indexOf("L_total_km");
            int iA = cols.indexOf("L_half_A_km");
            int iB = cols.indexOf("L_half_B_km");
            int iSource = cols.indexOf("source_sheet");
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] p = line.split(",", -1);
                String route = p[iRoute].trim();
                String source = p[iSource].trim();
                catalog.byRoute.put(route, new RouteVariants(
                        route, p[iName].trim(), parse(p[iTotal]),
                        new VariantSpec(route, route + "-half-A", parse(p[iA]), source),
                        new VariantSpec(route, route + "-half-B", parse(p[iB]), source)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return catalog;
    }

    private static double parse(String v) {
        return v == null || v.isBlank() ? Double.NaN : Double.parseDouble(v.trim());
    }

    public RouteVariants get(String route) {
        return byRoute.get(route);
    }

    public int size() {
        return byRoute.size();
    }

    public List<RouteVariants> closureFlags(double maxShare) {
        List<RouteVariants> flags = new ArrayList<>();
        for (RouteVariants rv : byRoute.values()) {
            double err = rv.closureErrorShare();
            if (Double.isNaN(err) || err > maxShare) flags.add(rv);
        }
        return flags;
    }
}
