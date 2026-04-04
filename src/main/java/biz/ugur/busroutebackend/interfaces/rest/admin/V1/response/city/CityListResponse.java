package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.city;

import biz.ugur.busroutebackend.admin.application.dto.city.CityList;
import biz.ugur.busroutebackend.shared.application.dto.PaginationInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;


@Getter
@ToString
@EqualsAndHashCode
public class CityListResponse {

    @JsonProperty("cities")
    private final List<CityResponse> cities;

    @JsonProperty("active_count")
    private final Long activeCount;

    @JsonProperty("pagination")
    private final PaginationInfo pagination;

    public CityListResponse(List<CityResponse> cities, Long activeCount, PaginationInfo pagination) {
        this.cities = cities;
        this.activeCount = activeCount;
        this.pagination = pagination;
    }

    public static CityListResponse fromResult(CityList cityList) {
        return new CityListResponse(
                cityList.getCities()
                        .stream()
                        .map(CityResponse::fromResult)
                        .toList(),
                cityList.getActiveCount(),
                cityList.getPagination()
        );
    }
}
