package biz.ugur.busroutebackend.migration.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@AllArgsConstructor
public class MigrationStatus {
    private Map<String, Object> sourceStats;
    private Map<String, Object> targetStats;
    private String sourceConnectionError;
    private String targetConnectionError;
    private boolean migrationComplete;
    private LocalDateTime lastChecked;

    public MigrationStatus() {
        this.lastChecked = LocalDateTime.now();
    }

    public boolean isHealthy() {
        return sourceConnectionError == null && targetConnectionError == null;
    }

    public double getMigrationProgress() {
        if (sourceStats == null || targetStats == null) return 0.0;

        Integer sourceTotal = (Integer) sourceStats.get("stops");
        Integer migratedStops = (Integer) targetStats.get("migratedStops");

        if (sourceTotal == null || migratedStops == null || sourceTotal == 0) return 0.0;

        return Math.min((double) migratedStops / sourceTotal * 100, 100.0);
    }
}
