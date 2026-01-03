package biz.ugur.busroutebackend.transport.application.dto.assignment;

import java.util.List;

public record ExcelImportResult(
        List<RouteAssignmentData> created,
        List<FailedImportRow> failed,
        int successCount,
        int failedCount,
        int totalRows
) {
    public record FailedImportRow(
            int rowNumber,
            String licensePlate,
            String error
    ) {
    }
}
