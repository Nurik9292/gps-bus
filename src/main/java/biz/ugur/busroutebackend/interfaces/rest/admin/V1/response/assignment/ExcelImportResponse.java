package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.assignment;

import biz.ugur.busroutebackend.transport.application.dto.assignment.ExcelImportResult;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ExcelImportResponse(
        List<RouteAssignmentResponse> created,
        List<FailedRow> failed,
        @JsonProperty("successCount")
        int successCount,
        @JsonProperty("failedCount")
        int failedCount,
        @JsonProperty("totalRows")
        int totalRows
) {

    public record FailedRow(
            @JsonProperty("rowNumber")
            int rowNumber,
            @JsonProperty("licensePlate")
            String licensePlate,
            String error
    ) {
    }

    public static ExcelImportResponse fromResult(ExcelImportResult result) {
        List<RouteAssignmentResponse> createdResponses = result.created().stream()
                .map(RouteAssignmentResponse::fromData)
                .toList();

        List<FailedRow> failedRows = result.failed().stream()
                .map(f -> new FailedRow(f.rowNumber(), f.licensePlate(), f.error()))
                .toList();

        return new ExcelImportResponse(
                createdResponses,
                failedRows,
                result.successCount(),
                result.failedCount(),
                result.totalRows()
        );
    }
}
