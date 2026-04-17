package biz.ugur.busroutebackend.transport.infrastructure.excel;

import biz.ugur.busroutebackend.transport.application.dto.assignment.ExcelImportResult;
import biz.ugur.busroutebackend.transport.application.dto.assignment.ImportFromExcelCommand;
import biz.ugur.busroutebackend.transport.application.dto.assignment.ImportJobStatus;
import biz.ugur.busroutebackend.transport.application.usecase.assignment.ImportRouteAssignmentsFromExcelUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
public class AsyncExcelImportService {

    private static final String KEY_PREFIX = "import_job:";
    private static final Duration JOB_TTL = Duration.ofHours(1);

    private final ImportRouteAssignmentsFromExcelUseCase importUseCase;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public AsyncExcelImportService(ImportRouteAssignmentsFromExcelUseCase importUseCase,
                                    ReactiveRedisTemplate<String, Object> redisTemplate,
                                    ObjectMapper objectMapper) {
        this.importUseCase = importUseCase;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Mono<String> submitJob(byte[] fileContent, String assignedBy) {
        String jobId = UUID.randomUUID().toString();
        ImportJobStatus pending = ImportJobStatus.pending(jobId);
        LocalDateTime startedAt = LocalDateTime.now();

        return saveStatus(jobId, pending)
                .doOnSuccess(saved -> {
                    log.info("[import-job] Submitted: jobId={} by={}", jobId, assignedBy);
                    runInBackground(jobId, fileContent, assignedBy, startedAt);
                })
                .thenReturn(jobId);
    }

    public Mono<ImportJobStatus> getJobStatus(String jobId) {
        return redisTemplate.opsForValue()
                .get(KEY_PREFIX + jobId)
                .map(raw -> objectMapper.convertValue(raw, ImportJobStatus.class))
                .switchIfEmpty(Mono.empty());
    }

    private void runInBackground(String jobId, byte[] fileContent, String assignedBy, LocalDateTime startedAt) {
        ImportFromExcelCommand command = new ImportFromExcelCommand(fileContent, assignedBy);

        Mono.just(command)
                .as(importUseCase::execute)
                .flatMap(result -> {
                    ImportJobStatus completed = ImportJobStatus.completed(jobId, result, startedAt);
                    log.info("[import-job] Completed: jobId={} success={} failed={} total={}",
                            jobId, result.successCount(), result.failedCount(), result.totalRows());
                    return saveStatus(jobId, completed);
                })
                .onErrorResume(error -> {
                    log.error("[import-job] Failed: jobId={} error={}", jobId, error.getMessage(), error);
                    ImportJobStatus failed = ImportJobStatus.failed(jobId, error.getMessage(), startedAt);
                    return saveStatus(jobId, failed);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    private Mono<Boolean> saveStatus(String jobId, ImportJobStatus status) {
        return redisTemplate.opsForValue()
                .set(KEY_PREFIX + jobId, status, JOB_TTL);
    }
}
