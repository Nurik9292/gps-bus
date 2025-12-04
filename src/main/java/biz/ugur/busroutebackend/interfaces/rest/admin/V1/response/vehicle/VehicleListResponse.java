package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.vehicle;

import biz.ugur.busroutebackend.shared.application.dto.PaginationInfo;
import biz.ugur.busroutebackend.transport.application.dto.VehicleData;
import biz.ugur.busroutebackend.transport.application.dto.VehicleListResult;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record VehicleListResponse(
        @JsonProperty("vehicles")
        List<VehicleResponse> vehicles,

        @JsonProperty("pagination")
        PaginationInfo pagination,

        @JsonProperty("total_count")
        Long totalCount,

        @JsonProperty("active_count")
        Long activeCount
) {
    public static VehicleListResponse fromResult(VehicleListResult result) {
        List<VehicleResponse> vehicleResponses = result.vehicles().stream()
                .map(VehicleResponse::fromData)
                .toList();

        return new VehicleListResponse(
                vehicleResponses,
                result.pagination(),
                result.totalCount(),
                result.activeCount()
        );
    }

    public static VehicleListResponse fromList(List<VehicleData> vehicles) {
        List<VehicleResponse> vehicleResponses = vehicles.stream()
                .map(VehicleResponse::fromData)
                .toList();

        long activeCount = vehicles.stream().filter(VehicleData::isActive).count();

        return new VehicleListResponse(
                vehicleResponses,
                null,
                (long) vehicles.size(),
                activeCount
        );
    }
}
