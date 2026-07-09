package biz.ugur.busroutebackend.replay;

import biz.ugur.busroutebackend.prediction.core.GpsFix;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class GpsFixJsonl {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private GpsFixJsonl() {}

    public static List<GpsFix> read(Path file) {
        try {
            List<GpsFix> out = new ArrayList<>();
            for (String line : Files.readAllLines(file)) {
                if (line.isBlank()) continue;
                out.add(MAPPER.readValue(line, GpsFix.class));
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read fixes from " + file, e);
        }
    }

    public static void write(Path file, List<GpsFix> fixes) {
        try {
            List<String> lines = new ArrayList<>(fixes.size());
            for (GpsFix f : fixes) {
                lines.add(MAPPER.writeValueAsString(f));
            }
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.write(file, lines);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write fixes to " + file, e);
        }
    }
}
