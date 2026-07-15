package biz.ugur.busroutebackend.catalogsearch.domain.exceptions;

public class CatalogSearchValidationException extends CatalogSearchDomainException {

    public CatalogSearchValidationException(String businessCode, String message) {
        super("CATALOG_SEARCH.VALIDATION_ERROR", businessCode, message);
    }
}
