package biz.ugur.busroutebackend.interfaces.rest.admin.response.city;

import biz.ugur.busroutebackend.admin.application.dto.city.CityList;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class CityListResponse {

    @JsonProperty("cities")
    private List<CityResponse> cities;

    @JsonProperty("total_count")
    private Integer totalCount;

    @JsonProperty("active_count")
    private Long activeCount;

    public CityListResponse(List<CityResponse> cities, Long activeCount) {
        this.cities = cities;
        this.totalCount = cities.size();
        this.activeCount = activeCount;
    }

    public static CityListResponse fromResult(CityList cityList) {
        return new CityListResponse(
                cityList.getCities()
                        .stream()
                        .map(CityResponse::fromResult)
                        .toList(),
                cityList.getActiveCount()
        );
    }
}
