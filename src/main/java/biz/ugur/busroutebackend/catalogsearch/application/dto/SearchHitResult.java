package biz.ugur.busroutebackend.catalogsearch.application.dto;

import biz.ugur.busroutebackend.catalogsearch.domain.model.SearchHit;

public record SearchHitResult(String objectKind, String objectId, String title, String subtitle,
                              Double lat, Double lon, double score, String source) {

    public static SearchHitResult fromDomain(SearchHit hit) {
        return new SearchHitResult(hit.objectKind().name(), hit.objectId(), hit.title(),
                hit.subtitle(), hit.lat(), hit.lon(), hit.score(), hit.source());
    }
}
