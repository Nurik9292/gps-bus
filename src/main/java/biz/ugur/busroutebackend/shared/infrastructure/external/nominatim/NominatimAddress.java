package biz.ugur.busroutebackend.shared.infrastructure.external.nominatim;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NominatimAddress(
        String road,
        @JsonProperty("house_number") String houseNumber,
        String city,
        String suburb,
        String state
) {}
