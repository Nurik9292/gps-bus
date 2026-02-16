package biz.ugur.busroutebackend.shared.infrastructure.external.nominatim;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NominatimPlace(
        @JsonProperty("place_id") Long placeId,
        @JsonProperty("display_name") String displayName,
        String lat,
        String lon,
        String type,
        @JsonProperty("address") NominatimAddress address,
        Double importance
) {}
