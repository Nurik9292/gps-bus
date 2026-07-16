package biz.ugur.busroutebackend.catalogsearch.domain.model;

import java.util.Arrays;

public enum CatalogObjectKind {
    STOP,
    ROUTE,
    PLACE,
    STREET;

    public boolean aliasable() {
        return this == STOP || this == ROUTE;
    }

    public static CatalogObjectKind fromString(String raw) {
        return Arrays.stream(values())
                .filter(k -> k.name().equalsIgnoreCase(raw))
                .findFirst()
                .orElse(null);
    }
}
