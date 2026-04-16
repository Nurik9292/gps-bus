package biz.ugur.busroutebackend.advertising.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RejectAdPlacementCommand(
        @JsonProperty("reason") String reason
) {}
