package biz.ugur.busroutebackend.catalogsearch.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.exception.AbstractDomainException;
import lombok.Getter;

@Getter
public abstract class CatalogSearchDomainException extends AbstractDomainException {

    private final String businessCode;

    protected CatalogSearchDomainException(String errorCode, String businessCode, String message) {
        super(errorCode, message);
        this.businessCode = businessCode;
    }
}
