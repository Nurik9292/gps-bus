package biz.ugur.busroutebackend.shared.test.integration;

import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePositionPredictionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class GpsPipelineReplayRunner {

    private static final String GOLDEN_REGENERATE_PROP = "golden.regenerate";
    private static final String FIXTURES_DIR = "fixtures/gps";
    private static final String GOLDEN_DIR = "fixtures/gps/golden";

    private final VehiclePositionPredictionService service;
    private final ObjectMapper mapper;

    public GpsPipelineReplayRunner(VehiclePositionPredictionService service) {
        this.service = service;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public List<List<PredictionSnapshot>> replay(String scenario) throws IOException {
        List<GpsFixture> events = loadFixtures(scenario);
        if (events.isEmpty()) {
            throw new IllegalStateException("No events in fixture: " + scenario);
        }

        Duration timeShift = Duration.between(
                events.get(0).wallClock(), Instant.now().minusSeconds(2));

        List<List<PredictionSnapshot>> snapshots = new ArrayList<>(events.size());
        for (GpsFixture event : events) {
            Instant shiftedTimestamp = event.timestamp().plus(timeShift);
            service.onGpsUpdate(
                    event.vehicleId(),
                    event.licensePlate(),
                    event.routeNumber(),
                    event.latitude(),
                    event.longitude(),
                    event.speedKmh(),
                    event.course(),
                    event.inMotion(),
                    shiftedTimestamp,
                    event.direction()
            );
            snapshots.add(PredictionSnapshot.fromAll(service.getActiveStates()));
        }

        compareOrRegenerate(scenario, snapshots);
        return snapshots;
    }

    private List<GpsFixture> loadFixtures(String scenario) throws IOException {
        Path path = resourcePath(FIXTURES_DIR, scenario + ".jsonl");
        if (!Files.exists(path)) {
            throw new IllegalStateException(
                    "Fixture not found: " + path + ". Record one via GpsRecorder first.");
        }
        List<GpsFixture> events = new ArrayList<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            events.add(mapper.readValue(line, GpsFixture.class));
        }
        return events;
    }

    private void compareOrRegenerate(String scenario, List<List<PredictionSnapshot>> snapshots)
            throws IOException {
        Path goldenPath = resourcePath(GOLDEN_DIR, scenario + "-golden.json");
        String actual = mapper.writeValueAsString(snapshots);

        boolean regenerate = Boolean.parseBoolean(System.getProperty(GOLDEN_REGENERATE_PROP, "false"));
        if (regenerate || !Files.exists(goldenPath)) {
            Files.createDirectories(goldenPath.getParent());
            Files.writeString(goldenPath, actual, StandardCharsets.UTF_8);
            if (regenerate) {
                System.out.println("[GOLDEN] regenerated: " + goldenPath);
            } else {
                System.out.println("[GOLDEN] created baseline: " + goldenPath);
            }
            return;
        }

        String expected = Files.readString(goldenPath, StandardCharsets.UTF_8);
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    "Golden snapshot mismatch for scenario '" + scenario + "'.\n" +
                            "Expected (" + goldenPath + "):\n" + expected +
                            "\nActual:\n" + actual +
                            "\n\nTo accept changes: re-run with -Dgolden.regenerate=true");
        }
    }

    private Path resourcePath(String dir, String file) {
        return Path.of("src/test/resources", dir, file);
    }
}
