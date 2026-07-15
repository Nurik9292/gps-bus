package biz.ugur.busroutebackend.catalogsearch.domain.exceptions;

public class AliasNotFoundException extends CatalogSearchDomainException {

    public AliasNotFoundException(Long aliasId) {
        super("CATALOG_SEARCH.NOT_FOUND", "ALIAS_NOT_FOUND",
                "Search alias not found with ID: " + aliasId);
    }
}
