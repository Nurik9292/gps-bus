package biz.ugur.busroutebackend.replay.pipeline;

import biz.ugur.busroutebackend.replay.GpsFix;
import biz.ugur.busroutebackend.replay.GpsFixJsonl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

public final class CorpusLoader {

    private CorpusLoader() {}

    public static List<Episode> load(Path corpusDir, double gapSplitSec, int minFixesPerEpisode) {
        List<GpsFix> all = new ArrayList<>();
        try (Stream<Path> files = Files.walk(corpusDir)) {
            files.filter(p -> p.toString().endsWith(".jsonl"))
                    .sorted()
                    .forEach(p -> all.addAll(GpsFixJsonl.read(p)));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot walk corpus dir " + corpusDir, e);
        }
        Map<String, List<GpsFix>> byVehicle = new TreeMap<>();
        for (GpsFix f : all) {
            byVehicle.computeIfAbsent(f.vehicleId(), k -> new ArrayList<>()).add(f);
        }
        List<Episode> episodes = new ArrayList<>();
        byVehicle.forEach((vehicleId, fixes) -> {
            fixes.sort(Comparator.comparing(GpsFix::timestamp));
            List<GpsFix> current = new ArrayList<>();
            for (GpsFix f : fixes) {
                boolean split = !current.isEmpty()
                        && ((f.timestamp().toEpochMilli()
                             - current.get(current.size() - 1).timestamp().toEpochMilli()) / 1000.0 > gapSplitSec
                            || !f.routeNumber().equals(current.get(0).routeNumber()));
                if (split) {
                    addIfBigEnough(episodes, vehicleId, current, minFixesPerEpisode);
                    current = new ArrayList<>();
                }
                current.add(f);
            }
            addIfBigEnough(episodes, vehicleId, current, minFixesPerEpisode);
        });
        return episodes;
    }

    private static void addIfBigEnough(List<Episode> episodes, String vehicleId,
                                       List<GpsFix> fixes, int minFixes) {
        if (fixes.size() >= minFixes) {
            episodes.add(new Episode(vehicleId, fixes.get(0).routeNumber(), List.copyOf(fixes)));
        }
    }
}
