package biz.ugur.busroutebackend.catalogsearch.domain.exceptions;

import biz.ugur.busroutebackend.catalogsearch.domain.model.CatalogObjectKind;

public class AliasAlreadyExistsException extends CatalogSearchDomainException {

    public AliasAlreadyExistsException(CatalogObjectKind kind, String objectId, String aliasRaw) {
        super("CATALOG_SEARCH.ALREADY_EXISTS", "ALIAS_ALREADY_EXISTS",
                "Alias already exists for " + kind + "/" + objectId + ": " + aliasRaw);
    }
}
