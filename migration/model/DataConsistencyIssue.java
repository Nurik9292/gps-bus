package biz.ugur.busroutebackend.migration.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DataConsistencyIssue {
    private String type;
    private String description;
    private String severity; // LOW, MEDIUM, HIGH, CRITICAL
    private LocalDateTime detectedAt;

    public DataConsistencyIssue(String type, String description, String severity) {
        this.type = type;
        this.description = description;
        this.severity = severity;
        this.detectedAt = LocalDateTime.now();
    }
}