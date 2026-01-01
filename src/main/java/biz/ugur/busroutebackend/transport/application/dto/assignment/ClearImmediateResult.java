package biz.ugur.busroutebackend.transport.application.dto.assignment;

import java.time.Instant;

public record ClearImmediateResult(
        int clearedCount,
        Instant clearedAt,
        boolean success,
        String errorMessage
) {
}
