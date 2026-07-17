package biz.ugur.busroutebackend.prediction.core;

public record CoreConfig(
        double dtSec,
        double w0Meters,
        double kWindowPerSpeed,
        double sigmaMeasDefaultMeters,
        double accuracyRefMeters,
        double dSnapMeters,
        double dMaxMeters,
        double gammaGate,
        double qPos,
        double qVel,
        double pInitPos,
        double pInitVel,
        double rMaxRate,
        double rMaxBaseMeters,
        double weakZvWeight,
        int nPersist,
        int mReanchor,
        double tLostSec,
        double tMaxSec,
        double vTargetMs,
        double aDepMs2,
        double aMaxMs2,
        double vMaxMs,
        double dReanchorMeters,
        double recoveryPullFactor,
        double vStopKmh,
        double vMoveKmh,
        int hStop,
        int hDep,
        int hDec,
        double dwellMinSec,
        double dDecelMeters,
        double epsArrMeters,
        double epsStopMeters,
        double epsTermMeters,
        double dwellExpectedSec,
        double dwellMaxSec,
        int nTurnConfirm,
        double dTurnConfirmMeters,
        int unpinWindowTicks,
        int kTurnRevert,
        double wTurnWindowMeters,
        int historyNMin,
        int kOffRoute,
        int mOffRouteExit,
        double scoreLambda,
        double scoreRejectPenalty,
        double scoreProgressBonus,
        double sSwitch,
        int hSwitch,
        double dSwitchSmoothMeters,
        int maxHypotheses,
        boolean rHdopEnabled,
        double rHdopAMeters,
        double rHdopBMetersPerHdop,
        double gateNisThreshold,
        double qScale,
        double epsCloseTailMeters,
        int nTurnConfirmTerm,
        double dTurnConfirmTermMeters,
        int kTermMissOffRoute,
        int nDepMoveConfirm,
        int kConfirmFreeze,
        double rCityDeepMeters,
        double rCityPlateauMeters,
        double tCityDwellSec,
        int mCityFixes,
        int kCityExit,
        double dCityExitDeltaMeters,
        double gCitySpanGapSec,
        double tCityExitMinSpanSec,
        double tPostBoundaryGuardSec,
        int kConfirmPostBoundary,
        double epsMidlineMeters,
        double dOnlineMeters,
        int kOffRouteReacq,
        double minReacqTravelMeters,
        double wTurnWindowMaxMeters,
        double turnTauNomSec,
        double turnVTargetMs,
        double dTermEscapeMeters) {

    public static CoreConfig defaults() {
        return new CoreConfig(
                1.0,
                150.0, 1.5,
                15.0, 5.0,
                80.0, 120.0,
                3.0,
                0.5 * qScaleFromSystemProperties(), 0.8 * qScaleFromSystemProperties(),
                40.0 * 40.0, 5.0 * 5.0,
                0.5, 30.0,
                0.1,
                4, 2,
                15.0, 90.0,
                12.5, 1.0, 1.5,
                22.0, 200.0, 4.0,
                1.0, 5.0,
                2, 3, 3,
                10.0,
                80.0, 60.0, 15.0, 30.0,
                20.0, 600.0,
                3, // nTurnConfirm [2;4]: Шаг-5 пере-ратифицировано 14.07.2026, СТОП-В, контракт-гейт (n=2 отклонён)
                40.0,
                5, // unpinWindowTicks: память unpin №28(в); эмпирически H_sw+2 на bring-up A11 — связь не формульная, калибруется отдельно (Шаг 5)
                3,
                600.0, // wTurnWindowMeters [300;1200]: Шаг-5 калибровано 14.07.2026, CORPUS-E, свип results.tsv@2d504b43
                5,
                5,
                2,
                0.9, 1.0, 0.5,
                0.25, 3, 150.0,
                6,
                Boolean.getBoolean("r.hdop.enabled"),
                Double.parseDouble(System.getProperty("r.hdop.a", "2.764")),
                Double.parseDouble(System.getProperty("r.hdop.b", "3.734")),
                Double.parseDouble(System.getProperty("gate.nis", "9.0")),
                qScaleFromSystemProperties(),
                150.0, // epsCloseTailMeters (№22″, К-4а): калибруется, Шаг 5
                5, // nTurnConfirmTerm (№23′, К-4б): калибруется, Шаг 5
                150.0, // dTurnConfirmTermMeters (№23′, К-4б): калибруется, Шаг 5
                5, // kTermMissOffRoute (№15′, К-2): калибруется, Шаг 5
                3, // nDepMoveConfirm (№15′, К-2): калибруется, Шаг 5
                2, // kConfirmFreeze (№22′, К-3): калибруется, Шаг 5
                300.0, // rCityDeepMeters (М5-A, узел D): калибруется, Шаг 5
                900.0, // rCityPlateauMeters (М5-A, узел P; Т-2 A-120726-2): калибруется, Шаг 5
                120.0, // tCityDwellSec (М5-A, F1-span): калибруется, Шаг 5
                5, // mCityFixes (М5-A): калибруется, Шаг 5
                5, // kCityExit (М5-C): калибруется, Шаг 5
                150.0, // dCityExitDeltaMeters (М5-C, ΔR): калибруется, Шаг 5
                600.0, // gCitySpanGapSec (М5-A, гэп-допуск F1-span): калибруется, Шаг 5
                30.0, // tCityExitMinSpanSec (М5-C, У-2 A-120726-2): калибруется, Шаг 5
                300.0, // tPostBoundaryGuardSec (§Fold-guard, Р/И3.4): калибруется, Шаг 5 [120;600]
                2, // kConfirmPostBoundary (§Fold-guard, образец К-3): калибруется, Шаг 5
                300.0, // epsMidlineMeters (§0.4 ред. П-ε, И3.8): калибруется, Шаг 5 [150;600]
                40.0, // dOnlineMeters (REACQ B-2026-07-13) [20;80]: Шаг-5 калибровано 14.07.2026, CORPUS-E + стенд-логи
                5, // kOffRouteReacq (REACQ B-2026-07-13) [3;8]: Шаг-5 калибровано 14.07.2026, CORPUS-E + стенд-логи
                300.0, // minReacqTravelMeters (REACQ B-2026-07-13) [100;600]: Шаг-5 калибровано 14.07.2026, CORPUS-E + стенд-логи
                6000.0, // wTurnWindowMaxMeters (K-1 cap, №31-К-1): Шаг-5, Phase-D sizing @ e4696081
                25.0, // turnTauNomSec (K-1 τ_nom, вычет штатного межфиксового): Шаг-5, Phase-D
                12.5, // turnVTargetMs (K-1 форма v2, оригинал №31; Д-валидация 91.8%): Шаг-5, Phase-D @ e4696081
                500.0); // dTermEscapeMeters (B-2026-07-15): накопленный ход терминального рана, безусловно снимающий двойной контроль
    }

    private static double qScaleFromSystemProperties() {
        return Double.parseDouble(System.getProperty("q.scale", "1.0"));
    }

    public CoreConfig withCityZoneParams(double rDeep, double rPlateau, double tDwell,
                                         int mRun, double gGap, double tExitMinSpan,
                                         int kExit, double deltaR, double tPostGuard,
                                         int kConfirmPB, double epsMid) {
        return new CoreConfig(dtSec, w0Meters, kWindowPerSpeed, sigmaMeasDefaultMeters, accuracyRefMeters, dSnapMeters, dMaxMeters, gammaGate, qPos, qVel, pInitPos, pInitVel, rMaxRate, rMaxBaseMeters, weakZvWeight, nPersist, mReanchor, tLostSec, tMaxSec, vTargetMs, aDepMs2, aMaxMs2, vMaxMs, dReanchorMeters, recoveryPullFactor, vStopKmh, vMoveKmh, hStop, hDep, hDec, dwellMinSec, dDecelMeters, epsArrMeters, epsStopMeters, epsTermMeters, dwellExpectedSec, dwellMaxSec, nTurnConfirm, dTurnConfirmMeters, unpinWindowTicks, kTurnRevert, wTurnWindowMeters, historyNMin, kOffRoute, mOffRouteExit, scoreLambda, scoreRejectPenalty, scoreProgressBonus, sSwitch, hSwitch, dSwitchSmoothMeters, maxHypotheses, rHdopEnabled, rHdopAMeters, rHdopBMetersPerHdop, gateNisThreshold, qScale, epsCloseTailMeters, nTurnConfirmTerm, dTurnConfirmTermMeters, kTermMissOffRoute, nDepMoveConfirm, kConfirmFreeze, rDeep, rPlateau, tDwell, mRun, kExit, deltaR, gGap, tExitMinSpan, tPostGuard, kConfirmPB, epsMid, dOnlineMeters, kOffRouteReacq, minReacqTravelMeters, wTurnWindowMaxMeters, turnTauNomSec, turnVTargetMs, dTermEscapeMeters);
    }
}
