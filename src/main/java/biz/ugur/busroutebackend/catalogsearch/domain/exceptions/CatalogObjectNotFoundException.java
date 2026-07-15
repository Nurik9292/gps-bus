package biz.ugur.busroutebackend.catalogsearch.domain.exceptions;

import biz.ugur.busroutebackend.catalogsearch.domain.model.CatalogObjectKind;

public class CatalogObjectNotFoundException extends CatalogSearchDomainException {

    public CatalogObjectNotFoundException(CatalogObjectKind kind, String objectId) {
        super("CATALOG_SEARCH.NOT_FOUND", "OBJECT_NOT_FOUND",
                "Catalog object not found: " + kind + "/" + objectId);
    }
}
