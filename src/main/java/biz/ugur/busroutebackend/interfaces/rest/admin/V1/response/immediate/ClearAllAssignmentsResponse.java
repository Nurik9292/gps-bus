package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.immediate;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ClearAllAssignmentsResponse(
        @JsonProperty("cleared_count")
        int clearedCount,

        @JsonProperty("cleared_at")
        String clearedAt,

        boolean success,

        @JsonProperty("error_message")
        String errorMessage
) {}
