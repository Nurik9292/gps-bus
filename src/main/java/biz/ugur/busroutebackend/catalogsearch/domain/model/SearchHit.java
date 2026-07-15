package biz.ugur.busroutebackend.catalogsearch.domain.model;

public record SearchHit(CatalogObjectKind objectKind, String objectId,
                        String title, String subtitle,
                        Double lat, Double lon,
                        double score, String source) {
}
