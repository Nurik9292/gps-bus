package biz.ugur.busroutebackend.transport.domain.valueobject;

public record SegmentBaseline(String fromStopId, String toStopId,
                              double weightedAvgSeconds, long totalSamples) {
}
