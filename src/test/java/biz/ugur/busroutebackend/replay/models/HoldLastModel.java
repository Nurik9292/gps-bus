package biz.ugur.busroutebackend.replay.models;

import biz.ugur.busroutebackend.replay.GeometryFixture;
import biz.ugur.busroutebackend.replay.GpsFix;
import biz.ugur.busroutebackend.replay.PredictionModel;

public class HoldLastModel implements PredictionModel {

    private final GeometricSnapModel snap = new GeometricSnapModel();
    private Estimate last;

    @Override
    public Estimate onFix(GpsFix fix, GeometryFixture g) {
        if (last == null) {
            Estimate first = snap.onFix(fix, g);
            last = new Estimate(first.s(), 0.0, "HOLD", first.varianceS());
        }
        return last;
    }

    @Override
    public void reset() {
        last = null;
    }
}
