package biz.ugur.busroutebackend.transport.application.dto;

import biz.ugur.busroutebackend.transport.application.usecase.UpdateVehiclePositionsUseCase;
import lombok.Data;

import java.time.Instant;
import java.util.List;

public record VehiclePositionUpdateResult(
        long updatedCount,
        long createdCount,
        long failedCount,
        long invalidCount,
        long conflictCount,
        Instant processedAt,
        List<UpdateVehiclePositionsUseCase.VehicleUpdateStatus> details) {

    public long getTotalProcessed() {
        return updatedCount + createdCount + failedCount + invalidCount + conflictCount;
    }

    public boolean isSuccessful() {
        return failedCount == 0 && invalidCount == 0 && conflictCount == 0;
    }

    public static VehiclePositionUpdateResult failed(int batchSize) {
        return new VehiclePositionUpdateResult(0, 0, batchSize, 0, 0, Instant.now(), List.of());
    }

    public static VehiclePositionUpdateResult merge(VehiclePositionUpdateResult r1, VehiclePositionUpdateResult r2) {
        return new VehiclePositionUpdateResult(
                r1.updatedCount() + r2.updatedCount(),
                r1.createdCount() + r2.createdCount(),
                r1.failedCount() + r2.failedCount(),
                r1.invalidCount() + r2.invalidCount(),
                r1.conflictCount() + r2.conflictCount(),
                Instant.now(),
                List.of()
        );
    }

}