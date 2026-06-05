package biz.ugur.busroutebackend.client.application.dto;

import biz.ugur.busroutebackend.client.domain.model.Client;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record ClientSummary(
        @JsonProperty("client_id")     String clientId,
        @JsonProperty("name")          String name,
        @JsonProperty("phone")         String phone,
        @JsonProperty("status")        String status,
        @JsonProperty("platform")      String platform,
        @JsonProperty("last_activity") LocalDateTime lastActivity
) {
    public static ClientSummary fromDomain(Client client) {
        return new ClientSummary(
                client.getId().getValue(),
                client.getName(),
                client.getPhoneNumber(),
                client.getStatus() != null ? client.getStatus().name() : null,
                client.getPlatform() != null ? client.getPlatform().name() : null,
                client.getLastActivity()
        );
    }
}
