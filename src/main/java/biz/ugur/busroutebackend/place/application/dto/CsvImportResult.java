package biz.ugur.busroutebackend.place.application.dto;

import java.util.List;

public record CsvImportResult(
        int imported,
        int skipped,
        List<String> errors
) {}
