package biz.ugur.busroutebackend.place.infrastructure.geocoding;

import biz.ugur.busroutebackend.place.application.dto.GeocodingResult;
import biz.ugur.busroutebackend.place.domain.services.GeocodingService;
import biz.ugur.busroutebackend.shared.infrastructure.external.nominatim.NominatimApiClient;
import biz.ugur.busroutebackend.shared.infrastructure.external.nominatim.NominatimPlace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

@Service
@Slf4j
public class NominatimGeocodingService implements GeocodingService {

    private final NominatimApiClient nominatimClient;

    public NominatimGeocodingService(@Nullable NominatimApiClient nominatimClient) {
        this.nominatimClient = nominatimClient;
        if (nominatimClient == null) {
            log.info("Nominatim is disabled — geocoding will return empty results");
        }
    }

    @Override
    public Mono<List<GeocodingResult>> search(String query, int limit) {
        if (nominatimClient == null) {
            return Mono.just(List.of());
        }
        return nominatimClient.search(query, "tm", limit)
                .map(places -> places.stream().map(this::toGeocodingResult).toList());
    }

    @Override
    public Mono<GeocodingResult> reverse(double lat, double lon) {
        if (nominatimClient == null) {
            return Mono.empty();
        }
        return nominatimClient.reverse(lat, lon)
                .map(this::toGeocodingResult);
    }

    private GeocodingResult toGeocodingResult(NominatimPlace place) {
        String address = null;
        String houseNumber = null;
        String street = null;
        String city = null;

        if (place.address() != null) {
            street = place.address().road();
            houseNumber = place.address().houseNumber();
            city = place.address().city();
            address = buildAddress(street, houseNumber, city);
        }

        return new GeocodingResult(
                place.displayName(),
                address,
                houseNumber,
                street,
                city,
                place.lat() != null ? new BigDecimal(place.lat()) : null,
                place.lon() != null ? new BigDecimal(place.lon()) : null,
                "NOMINATIM"
        );
    }

    private String buildAddress(String street, String houseNumber, String city) {
        StringBuilder sb = new StringBuilder();
        if (street != null) {
            sb.append(street);
            if (houseNumber != null) {
                sb.append(", ").append(houseNumber);
            }
        }
        if (city != null) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(city);
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
