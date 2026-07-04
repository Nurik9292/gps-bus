package biz.ugur.busroutebackend.replay.pipeline;

import biz.ugur.busroutebackend.replay.GpsFix;

import java.util.List;

public record Episode(String vehicleId, String routeNumber, List<GpsFix> fixes) {

    public double durationSec() {
        if (fixes.size() < 2) return 0;
        return (fixes.get(fixes.size() - 1).timestamp().toEpochMilli()
                - fixes.get(0).timestamp().toEpochMilli()) / 1000.0;
    }

    public double nullAccuracyShare() {
        if (fixes.isEmpty()) return 0;
        long nulls = fixes.stream().filter(f -> f.accuracy() == null).count();
        return (double) nulls / fixes.size();
    }
}
