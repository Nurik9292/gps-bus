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


}