package biz.ugur.busroutebackend.migration.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@AllArgsConstructor
public class MigrationResult {
    private boolean success;
    private String errorMessage;
    private long durationMs;
    private int migratedStops;
    private int migratedRoutes;
    private int migratedRouteStops;
    private Map<String, Object> validationDetails;
    private LocalDateTime completedAt;

    public MigrationResult() {
        this.completedAt = LocalDateTime.now();
    }

    public String getSummary() {
        if (success) {
            return String.format("✅ Migration successful: %d stops, %d routes, %d route-stops in %dms",
                    migratedStops, migratedRoutes, migratedRouteStops, durationMs);
        } else {
            return String.format("❌ Migration failed: %s", errorMessage);
        }
    }

    public String getDetailedReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== MIGRATION REPORT ===\n");
        report.append("Status: ").append(success ? "SUCCESS" : "FAILED").append("\n");
        report.append("Duration: ").append(durationMs).append("ms\n");
        report.append("Completed at: ").append(completedAt).append("\n\n");

        if (success) {
            report.append("Migrated Data:\n");
            report.append("- Stops: ").append(migratedStops).append("\n");
            report.append("- Routes: ").append(migratedRoutes).append("\n");
            report.append("- Route-Stops: ").append(migratedRouteStops).append("\n");

            if (validationDetails != null) {
                report.append("\nValidation Details:\n");
                validationDetails.forEach((key, value) ->
                        report.append("- ").append(key).append(": ").append(value).append("\n"));
            }
        } else {
            report.append("Error: ").append(errorMessage).append("\n");
        }

        return report.toString();
    }
}