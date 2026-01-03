package biz.ugur.busroutebackend.transport.application.dto.assignment;

import java.time.LocalDate;
import java.time.LocalTime;

public record ExcelImportRow(
        int rowNumber,
        String licensePlate,
        String routeNumber,
        LocalDate effectiveDate,
        LocalTime expiresTime,
        String shiftType
) {
}
