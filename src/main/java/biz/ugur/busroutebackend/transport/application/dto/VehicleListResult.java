package biz.ugur.busroutebackend.transport.application.dto;

import biz.ugur.busroutebackend.shared.application.dto.PaginationInfo;

import java.util.List;

public record VehicleListResult(
        List<VehicleData> vehicles,
        PaginationInfo pagination,
        Long totalCount,
        Long activeCount
) {
    public static VehicleListResult of(List<VehicleData> vehicles, PaginationInfo pagination, Long totalCount, Long activeCount) {
        return new VehicleListResult(vehicles, pagination, totalCount, activeCount);
    }
}
