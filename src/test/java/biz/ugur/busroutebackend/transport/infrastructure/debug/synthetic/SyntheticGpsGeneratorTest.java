package biz.ugur.busroutebackend.transport.infrastructure.debug.synthetic;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.transport.infrastructure.debug.synthetic.SyntheticGpsGenerator.GeneratedTrack;
import biz.ugur.busroutebackend.transport.infrastructure.debug.synthetic.SyntheticGpsGenerator.Spec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SyntheticGpsGeneratorTest {

    private final SyntheticGpsGenerator generator = new SyntheticGpsGenerator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<double[]> STRAIGHT_LINE = List.of(
            new double[]{37.9500, 58.3400},
            new double[]{37.9500, 58.3700});

    private Spec spec(int count, double startArc) {
        return new Spec(STRAIGHT_LINE, startArc, 5.0, count,
                Instant.parse("2026-07-01T06:00:00Z"), "veh-syn", "0001 SYN", "8", 0);
    }

    @Test
    void departureRampAcceleratesFromZeroWithMonotonicArc() {
        GeneratedTrack track = generator.departureRamp(spec(6, 0.0), 15.0, 1.0);

        assertThat(track.fixes()).hasSize(6);
        assertThat(track.fixes().get(0).speedKmh()).isLessThan(0.1);
        for (int i = 1; i < track.fixes().size(); i++) {
            assertThat(track.fixes().get(i).speedKmh())
                    .as("speed non-decreasing on ramp")
                    .isGreaterThanOrEqualTo(track.fixes().get(i - 1).speedKmh());
            assertThat(track.trueArcLengthMeters()[i])
                    .as("true arc strictly advances")
                    .isGreaterThan(track.trueArcLengthMeters()[i - 1]);
        }
        assertThat(track.fixes().get(5).speedKmh()).isCloseTo(15.0 * 3.6, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void systematicBiasEmitsFixesConstantlyAheadOfTruth() {
        double bias = 20.0;
        GeneratedTrack track = generator.systematicSnapBias(spec(5, 100.0), 10.0, bias);
        double[] cumDist = SyntheticGpsGenerator.cumulativeDistances(STRAIGHT_LINE);

        for (int i = 0; i < track.fixes().size(); i++) {
            double[] truePoint = SyntheticGpsGenerator.pointAtArc(
                    STRAIGHT_LINE, cumDist, track.trueArcLengthMeters()[i]);
            double emittedVsTrue = DistanceCalculationService.haversineDistanceMeters(
                    track.fixes().get(i).latitude(), track.fixes().get(i).longitude(),
                    truePoint[0], truePoint[1]);
            assertThat(emittedVsTrue)
                    .as("emitted GPS is a constant forward snap bias ahead of truth")
                    .isCloseTo(bias, org.assertj.core.data.Offset.offset(1.0));
        }
        assertThat(track.trueArcLengthMeters()[4]).isGreaterThan(track.trueArcLengthMeters()[0]);
    }

    @Test
    void toJsonlProducesRecorderFormatWithQualityFields() throws Exception {
        GeneratedTrack track = generator.departureRamp(spec(3, 0.0), 12.0, 1.0);

        List<String> lines = SyntheticGpsGenerator.toJsonl(track, objectMapper);

        assertThat(lines).hasSize(3);
        Map<String, Object> event = objectMapper.readValue(lines.get(0), Map.class);
        assertThat(event).containsKeys("latitude", "longitude", "speedKmh", "course",
                "timestamp", "direction", "hdop", "satellites", "accuracy", "wallClock");
    }
}
