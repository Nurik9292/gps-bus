package biz.ugur.busroutebackend.business.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SuspendBusinessCommand(
        @JsonProperty("reason") String reason
) {}
