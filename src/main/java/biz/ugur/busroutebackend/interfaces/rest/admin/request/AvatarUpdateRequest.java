package biz.ugur.busroutebackend.interfaces.rest.admin.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AvatarUpdateRequest(
        @JsonProperty("avatar")
        String avatar
) {
}
