package biz.ugur.busroutebackend.subscription.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public record PaymentAttemptsSummary(
        @JsonProperty("total")     long total,
        @JsonProperty("success")   long success,
        @JsonProperty("failed")    long failed,
        @JsonProperty("pending")   long pending,
        @JsonProperty("by_status") Map<String, Long> byStatus
) {
    public static PaymentAttemptsSummary fromStatusCounts(Map<String, Long> statusCounts) {
        long total = 0;
        long success = 0;
        long failed = 0;
        long pending = 0;
        for (Map.Entry<String, Long> entry : statusCounts.entrySet()) {
            long count = entry.getValue() != null ? entry.getValue() : 0;
            total += count;
            switch (PaymentOutcome.fromPaymentStatus(entry.getKey())) {
                case SUCCESS -> success += count;
                case FAILED -> failed += count;
                case PENDING -> pending += count;
            }
        }
        return new PaymentAttemptsSummary(total, success, failed, pending,
                Collections.unmodifiableMap(new HashMap<>(statusCounts)));
    }
}
