package biz.ugur.busroutebackend.catalogsearch.application.dto;

public record RebuildResult(long inserted, long orphanAliases, long durationMs) {
}
