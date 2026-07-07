package biz.ugur.busroutebackend.replay;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public record GeometryFixture(
        String routeNumber,
        int direction,
        double totalMeters,
        List<double[]> points,
        double[] cumDist,
        List<StopPoint> stops,
        String topology,
        TerminalZone terminalZone) {

    public static final String TOPOLOGY_THERE_AND_BACK = "there-and-back";
    public static final String TOPOLOGY_LOOP = "loop";

    public record StopPoint(String stopId, int seq, double sMeters) {}

    public record TerminalZone(double lat, double lon, double radiusMeters) {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public GeometryFixture withStops(List<StopPoint> newStops) {
        return new GeometryFixture(routeNumber, direction, totalMeters, points, cumDist, newStops,
                topology, terminalZone);
    }

    public GeometryFixture withTopology(String newTopology) {
        return new GeometryFixture(routeNumber, direction, totalMeters, points, cumDist, stops,
                newTopology, terminalZone);
    }

    public GeometryFixture withTerminalZone(TerminalZone zone) {
        return new GeometryFixture(routeNumber, direction, totalMeters, points, cumDist, stops,
                topology, zone);
    }

    public boolean isLoop() {
        return TOPOLOGY_LOOP.equals(topology);
    }

    public static GeometryFixture fromPolyline(String routeNumber, int direction, List<double[]> latLonPoints) {
        double[] cum = new double[latLonPoints.size()];
        cum[0] = 0.0;
        for (int i = 1; i < latLonPoints.size(); i++) {
            cum[i] = cum[i - 1] + haversineMeters(
                    latLonPoints.get(i - 1)[0], latLonPoints.get(i - 1)[1],
                    latLonPoints.get(i)[0], latLonPoints.get(i)[1]);
        }
        return new GeometryFixture(routeNumber, direction, cum[cum.length - 1], latLonPoints, cum,
                List.of(), TOPOLOGY_THERE_AND_BACK, null);
    }

    public double sAtFraction(double fraction) {
        return fraction * totalMeters;
    }

    public double[] pointAtS(double s) {
        double target = Math.max(0, Math.min(s, totalMeters));
        int i = 0;
        while (i < cumDist.length - 2 && cumDist[i + 1] < target) i++;
        double segLen = cumDist[i + 1] - cumDist[i];
        double t = segLen == 0 ? 0 : (target - cumDist[i]) / segLen;
        double[] a = points.get(i);
        double[] b = points.get(i + 1);
        return new double[]{a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1])};
    }

    public void save(Path file) {
        try {
            var dto = new java.util.LinkedHashMap<String, Object>();
            dto.put("routeNumber", routeNumber);
            dto.put("direction", direction);
            dto.put("topology", topology);
            dto.put("totalMeters", totalMeters);
            dto.put("points", points);
            dto.put("cumDist", cumDist);
            var stopDtos = new java.util.ArrayList<Object>();
            for (StopPoint sp : stops) {
                var m = new java.util.LinkedHashMap<String, Object>();
                m.put("stopId", sp.stopId());
                m.put("seq", sp.seq());
                m.put("sMeters", sp.sMeters());
                stopDtos.add(m);
            }
            dto.put("stops", stopDtos);
            if (terminalZone != null) {
                var z = new java.util.LinkedHashMap<String, Object>();
                z.put("lat", terminalZone.lat());
                z.put("lon", terminalZone.lon());
                z.put("radiusMeters", terminalZone.radiusMeters());
                dto.put("terminalZone", z);
            }
            Files.createDirectories(file.toAbsolutePath().getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), dto);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static GeometryFixture load(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return fromTree(MAPPER.readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot load geometry fixture " + file, e);
        }
    }

    public static GeometryFixture loadClasspath(String resource) {
        try (InputStream in = GeometryFixture.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalArgumentException("no classpath resource " + resource);
            return fromTree(MAPPER.readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot load geometry fixture " + resource, e);
        }
    }

    private static GeometryFixture fromTree(com.fasterxml.jackson.databind.JsonNode n) {
        var pts = new java.util.ArrayList<double[]>();
        n.get("points").forEach(p -> pts.add(new double[]{p.get(0).asDouble(), p.get(1).asDouble()}));
        double[] cum = new double[n.get("cumDist").size()];
        for (int i = 0; i < cum.length; i++) cum[i] = n.get("cumDist").get(i).asDouble();
        var stops = new java.util.ArrayList<StopPoint>();
        if (n.has("stops")) {
            n.get("stops").forEach(s -> stops.add(new StopPoint(
                    s.get("stopId").asText(), s.get("seq").asInt(), s.get("sMeters").asDouble())));
        }
        String topology = n.has("topology") ? n.get("topology").asText() : TOPOLOGY_THERE_AND_BACK;
        TerminalZone zone = null;
        if (n.has("terminalZone")) {
            var z = n.get("terminalZone");
            zone = new TerminalZone(z.get("lat").asDouble(), z.get("lon").asDouble(),
                    z.get("radiusMeters").asDouble());
        }
        return new GeometryFixture(n.get("routeNumber").asText(), n.get("direction").asInt(),
                n.get("totalMeters").asDouble(), pts, cum, List.copyOf(stops), topology, zone);
    }

    public record Projection(double s, double distMeters) {}

    public Projection projectOntoRange(double lat, double lon, double sFrom, double sTo, double sDefault) {
        double best = Double.MAX_VALUE;
        double bestS = sDefault;
        for (int i = 0; i < points.size() - 1; i++) {
            if (cumDist[i + 1] < sFrom || cumDist[i] > sTo) continue;
            double[] a = points.get(i);
            double[] b = points.get(i + 1);
            double mLat = 111320.0;
            double mLon = 111320.0 * Math.cos(Math.toRadians((a[0] + b[0]) / 2));
            double dx = (b[1] - a[1]) * mLon;
            double dy = (b[0] - a[0]) * mLat;
            double l2 = dx * dx + dy * dy;
            double t = 0;
            if (l2 > 0) {
                double px = (lon - a[1]) * mLon;
                double py = (lat - a[0]) * mLat;
                t = Math.max(0, Math.min(1, (px * dx + py * dy) / l2));
            }
            double projLat = a[0] + t * (b[0] - a[0]);
            double projLon = a[1] + t * (b[1] - a[1]);
            double d = haversineMeters(lat, lon, projLat, projLon);
            if (d < best) {
                best = d;
                bestS = cumDist[i] + t * (cumDist[i + 1] - cumDist[i]);
            }
        }
        return new Projection(bestS, best);
    }

    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
