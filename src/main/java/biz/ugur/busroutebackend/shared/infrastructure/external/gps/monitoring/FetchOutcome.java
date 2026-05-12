package biz.ugur.busroutebackend.shared.infrastructure.external.gps.monitoring;

import java.time.Instant;

public sealed interface FetchOutcome
        permits FetchOutcome.Success, FetchOutcome.Empty, FetchOutcome.HttpError {

    record Success(int deviceCount, int freshCount, Instant latestFixTime) implements FetchOutcome {
    }

    record Empty() implements FetchOutcome {
    }

    record HttpError(Throwable cause) implements FetchOutcome {
    }
}
