package biz.ugur.busroutebackend.interfaces.rest.admin.request.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AvatarUpdateRequest(
        @JsonProperty("avatar")
        String avatar
) {
}
