package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.assignment;

import biz.ugur.busroutebackend.transport.application.dto.assignment.ClearImmediateResult;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ClearImmediateResponse(
        @JsonProperty("cleared_count")
        int clearedCount,

        @JsonProperty("cleared_at")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        Instant clearedAt,

        boolean success,

        @JsonProperty("error_message")
        String errorMessage
) {
    public static ClearImmediateResponse fromResult(ClearImmediateResult result) {
        return new ClearImmediateResponse(
                result.clearedCount(),
                result.clearedAt(),
                result.success(),
                result.errorMessage()
        );
    }
}
