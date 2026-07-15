package biz.ugur.busroutebackend.catalogsearch.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ugur.catalog-search")
@Getter
@Setter
public class CatalogSearchProperties {

    private boolean enabled = false;
    private double wordSimilarityThreshold = 0.6;
    private String rebuildCron = "0 0 4 * * *";
    private String rebuildZone = "Asia/Ashgabat";
    private long cacheTtlSeconds = 180;
}
