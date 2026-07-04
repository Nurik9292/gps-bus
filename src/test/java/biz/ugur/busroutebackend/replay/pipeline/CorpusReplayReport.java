package biz.ugur.busroutebackend.replay.pipeline;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;

public final class CorpusReplayReport {

    private CorpusReplayReport() {}

    public static String render(String corpusLabel, List<EpisodeReplayRunner.EpisodeStats> stats,
                                List<Episode> skippedNoGeometry) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Replay-отчёт корпуса: ").append(corpusLabel).append("\n\n");
        sb.append("Эпизодов прогнано: ").append(stats.size())
                .append("; пропущено без геометрии: ").append(skippedNoGeometry.size()).append("\n\n");

        sb.append("| Борт | Маршрут | Фиксы (дроп) | Длит., с | null-acc | ETA p95 60/120/300с (n) | "
                + "События A/S/D | Ре-привязки | Смены лидера | OFF/LOST доля | \\|ν\\| p50/p95 | NIS (n) | Рейсы |\n");
        sb.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (var s : stats) {
            sb.append(String.format(Locale.ROOT,
                    "| %s | %s | %d (%d) | %.0f | %.0f%% | %s / %s / %s | %d/%d/%d | %d | %d | %.0f%%/%.0f%% | %.1f/%.1f | %.2f (%d) | %d |%n",
                    s.vehicleId(), s.routeNumber(), s.fixesTotal(), s.fixesDropped(),
                    s.durationSec(), s.nullAccuracyShare() * 100,
                    fmtBucket(s.eta60()), fmtBucket(s.eta120()), fmtBucket(s.eta300()),
                    s.eventCounts().getOrDefault("DWELL_ENTER", 0L),
                    s.eventCounts().getOrDefault("SKIP", 0L),
                    s.eventCounts().getOrDefault("DECEL_ENTER", 0L),
                    s.recoveringSpells(), s.leaderSwitches(),
                    s.offRouteShare() * 100, s.gpsLostFrozenShare() * 100,
                    s.p50AbsInnovation(), s.p95AbsInnovation(),
                    s.meanNis(), s.nisN(), s.tripsCompleted()));
        }
        sb.append("\nСобытия: A=DWELL_ENTER (прибытия), S=SKIP, D=DECEL_ENTER. ")
                .append("NIS — средний по принятым снапам (ожидание ~1 при согласованных q/R). ")
                .append("Headline считается только на эпизодах с геометрией со стопами; ")
                .append("факт прибытия — П-2-детектор по сырым фиксам.\n");
        if (!skippedNoGeometry.isEmpty()) {
            sb.append("\nПропущены (нет фикстуры геометрии — добавить экспортёром): ");
            for (Episode ep : skippedNoGeometry) {
                sb.append(ep.vehicleId()).append("(route ").append(ep.routeNumber()).append(") ");
            }
            sb.append("\n");
        }
        sb.append("\nSHA-256 отчёта: ").append(sha256(sb.toString())).append("\n");
        sb.append("\nИсточник: `CorpusReplayTest` (не редактировать руками). ")
                .append("Живой корпус: `./mvnw test -Dtest=CorpusReplayTest -Dcorpus.dir=<путь>`\n");
        return sb.toString();
    }

    private static String fmtBucket(EpisodeReplayRunner.EtaBucket b) {
        if (b.n() == 0) return "—";
        return String.format(Locale.ROOT, "%.1fс(%d)", b.p95(), b.n());
    }

    public static String sha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) sb.append(String.format("%02x", h[i]));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
