package biz.ugur.busroutebackend.catalogsearch.domain.model;

import java.util.Arrays;
import java.util.Set;

public enum AliasSource {
    NAME,
    CURATED,
    COLLOQUIAL,
    POI;

    private static final Set<AliasSource> API_ALLOWED = Set.of(CURATED, COLLOQUIAL, POI);

    public boolean apiAllowed() {
        return API_ALLOWED.contains(this);
    }

    public static AliasSource fromString(String raw) {
        return Arrays.stream(values())
                .filter(s -> s.name().equalsIgnoreCase(raw))
                .findFirst()
                .orElse(null);
    }
}
