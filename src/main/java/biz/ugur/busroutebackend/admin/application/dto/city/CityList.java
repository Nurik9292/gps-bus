package biz.ugur.busroutebackend.admin.application.dto.city;

import lombok.Data;

import java.util.List;

@Data
public class CityList {

    List<CityResult> cities;
    Integer totalCount;
    Long activeCount;

    public CityList(List<CityResult> cities, Long activeCount) {
        this.cities = cities;
        this.totalCount = cities.size();
        this.activeCount = activeCount;
    }

}

