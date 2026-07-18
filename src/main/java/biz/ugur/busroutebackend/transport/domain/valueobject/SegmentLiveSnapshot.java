package biz.ugur.busroutebackend.transport.domain.valueobject;

import java.time.Instant;

public record SegmentLiveSnapshot(String fromStopId, String toStopId,
                                  double emaSeconds, long sampleCount, Instant observedAt) {
}
