package biz.ugur.busroutebackend.replay;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class OfflineContourPurityTest {

    private static final Path REPLAY_SRC = Path.of("src/test/java/biz/ugur/busroutebackend/replay");

    private static final Pattern FORBIDDEN = Pattern.compile(
            "import\\s+(reactor\\.|org\\.springframework\\."
            + "|biz\\.ugur\\.busroutebackend\\.transport\\.infrastructure\\.prediction"
            + "|biz\\.ugur\\.busroutebackend\\.transport\\.infrastructure\\.redis"
            + "|biz\\.ugur\\.busroutebackend\\.transport\\.application"
            + "|biz\\.ugur\\.busroutebackend\\.transport\\.scheduler)");

    @Test
    void replayContourImportsNoProductionPipelineNoReactorNoSpring() throws IOException {
        try (Stream<Path> files = Files.walk(REPLAY_SRC)) {
            List<Path> offenders = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            return FORBIDDEN.matcher(Files.readString(p)).find();
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    })
                    .toList();
            assertThat(offenders)
                    .as("оффлайн-контур replay не должен тянуть прод-пайплайн/Reactor/Spring")
                    .isEmpty();
        }
    }
}
