package biz.ugur.busroutebackend.transport.application.dto.assignment;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public record ImportJobStatus(
        @JsonProperty("job_id")          String jobId,
        @JsonProperty("status")          Status status,
        @JsonProperty("total_rows")      int totalRows,
        @JsonProperty("processed_rows")  int processedRows,
        @JsonProperty("success_count")   int successCount,
        @JsonProperty("failed_count")    int failedCount,
        @JsonProperty("failed")          List<ExcelImportResult.FailedImportRow> failed,
        @JsonProperty("error_message")   String errorMessage,

        @JsonProperty("started_at")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime startedAt,

        @JsonProperty("completed_at")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime completedAt
) {
    public enum Status {
        PENDING, PROCESSING, COMPLETED, FAILED
    }

    public static ImportJobStatus pending(String jobId) {
        return new ImportJobStatus(jobId, Status.PENDING, 0, 0, 0, 0,
                List.of(), null, LocalDateTime.now(), null);
    }

    public static ImportJobStatus processing(String jobId, int totalRows, int processedRows,
                                              int successCount, int failedCount,
                                              List<ExcelImportResult.FailedImportRow> failed) {
        return new ImportJobStatus(jobId, Status.PROCESSING, totalRows, processedRows,
                successCount, failedCount, failed, null, null, null);
    }

    public static ImportJobStatus completed(String jobId, ExcelImportResult result, LocalDateTime startedAt) {
        return new ImportJobStatus(jobId, Status.COMPLETED,
                result.totalRows(), result.totalRows(),
                result.successCount(), result.failedCount(),
                result.failed(), null, startedAt, LocalDateTime.now());
    }

    public static ImportJobStatus failed(String jobId, String errorMessage, LocalDateTime startedAt) {
        return new ImportJobStatus(jobId, Status.FAILED, 0, 0, 0, 0,
                List.of(), errorMessage, startedAt, LocalDateTime.now());
    }
}
