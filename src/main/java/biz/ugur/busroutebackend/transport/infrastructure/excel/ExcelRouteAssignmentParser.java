package biz.ugur.busroutebackend.transport.infrastructure.excel;

import biz.ugur.busroutebackend.transport.application.dto.assignment.ExcelImportRow;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ExcelRouteAssignmentParser {

    private static final int COL_LICENSE_PLATE = 0;
    private static final int COL_ROUTE_NUMBER = 1;
    private static final int COL_DATE = 2;
    private static final int COL_EXPIRES_TIME = 3;
    private static final int COL_SHIFT = 4;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM.dd.yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public Mono<List<ExcelImportRow>> parse(byte[] fileContent) {
        return Mono.fromCallable(() -> parseBlocking(fileContent))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(rows -> log.info("Parsed {} rows from Excel file", rows.size()))
                .doOnError(e -> log.error("Failed to parse Excel file", e));
    }

    private List<ExcelImportRow> parseBlocking(byte[] fileContent) throws IOException {
        List<ExcelImportRow> rows = new ArrayList<>();

        ZipSecureFile.setMinInflateRatio(0.001);

        try (ByteArrayInputStream bis = new ByteArrayInputStream(fileContent);
             Workbook workbook = new XSSFWorkbook(bis)) {

            Sheet sheet = workbook.getSheetAt(0);
            int lastRowNum = sheet.getLastRowNum();

            log.debug("Processing Excel sheet with {} rows", lastRowNum + 1);

            for (int rowIdx = 1; rowIdx <= lastRowNum; rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }

                try {
                    ExcelImportRow importRow = parseRow(row, rowIdx + 1);
                    rows.add(importRow);
                } catch (Exception e) {
                    log.warn("Failed to parse row {}: {}", rowIdx + 1, e.getMessage());
                    rows.add(new ExcelImportRow(
                            rowIdx + 1,
                            getCellValueAsString(row.getCell(COL_LICENSE_PLATE)),
                            null,
                            null,
                            null,
                            "PARSE_ERROR: " + e.getMessage()
                    ));
                }
            }
        }

        return rows;
    }

    private ExcelImportRow parseRow(Row row, int rowNumber) {
        String licensePlate = getCellValueAsString(row.getCell(COL_LICENSE_PLATE));
        String routeNumber = getCellValueAsString(row.getCell(COL_ROUTE_NUMBER));
        LocalDate effectiveDate = parseDate(row.getCell(COL_DATE));
        LocalTime expiresTime = parseTime(row.getCell(COL_EXPIRES_TIME));
        String shiftType = parseShiftType(row.getCell(COL_SHIFT));

        validateRow(licensePlate, routeNumber, effectiveDate, shiftType);

        return new ExcelImportRow(
                rowNumber,
                licensePlate,
                routeNumber,
                effectiveDate,
                expiresTime,
                shiftType
        );
    }

    private void validateRow(String licensePlate, String routeNumber, LocalDate effectiveDate, String shiftType) {
        if (licensePlate == null || licensePlate.isBlank()) {
            throw new IllegalArgumentException("License plate is required");
        }
        if (routeNumber == null || routeNumber.isBlank()) {
            throw new IllegalArgumentException("Route number is required");
        }
        if (effectiveDate == null) {
            throw new IllegalArgumentException("Date is required");
        }
        if (shiftType == null) {
            throw new IllegalArgumentException("Shift type is required");
        }
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }

        String result = switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double value = cell.getNumericCellValue();
                if (value == Math.floor(value)) {
                    yield String.valueOf((long) value);
                }
                yield String.valueOf(value);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> null;
        };

        return result != null ? result.trim() : null;
    }

    private LocalDate parseDate(Cell cell) {
        if (cell == null) {
            return null;
        }

        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }

        String dateStr = getCellValueAsString(cell);
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(dateStr, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            } catch (DateTimeParseException e2) {
                throw new IllegalArgumentException("Invalid date format: " + dateStr + ". Expected MM.dd.yyyy or dd.MM.yyyy");
            }
        }
    }

    private LocalTime parseTime(Cell cell) {
        if (cell == null) {
            return null;
        }

        if (cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalTime();
            }
            double fraction = cell.getNumericCellValue();
            int totalMinutes = (int) Math.round(fraction * 24 * 60);
            int hours = totalMinutes / 60;
            int minutes = totalMinutes % 60;
            return LocalTime.of(hours % 24, minutes);
        }

        String timeStr = getCellValueAsString(cell);
        if (timeStr == null || timeStr.isBlank()) {
            return null;
        }

        try {
            return LocalTime.parse(timeStr, TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid time format: " + timeStr + ". Expected HH:mm");
        }
    }

    private String parseShiftType(Cell cell) {
        String value = getCellValueAsString(cell);
        if (value == null || value.isBlank()) {
            return null;
        }

        return switch (value) {
            case "1", "FIRST", "first" -> "FIRST";
            case "2", "SECOND", "second" -> "SECOND";
            case "3", "FULL_DAY", "full_day", "FULLDAY", "fullday" -> "FULL_DAY";
            default -> throw new IllegalArgumentException("Invalid shift type: " + value + ". Expected 1, 2, 3 or FIRST, SECOND, FULL_DAY");
        };
    }

    private boolean isRowEmpty(Row row) {
        for (int i = 0; i < 5; i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getCellValueAsString(cell);
                if (value != null && !value.isBlank()) {
                    return false;
                }
            }
        }
        return true;
    }
}
