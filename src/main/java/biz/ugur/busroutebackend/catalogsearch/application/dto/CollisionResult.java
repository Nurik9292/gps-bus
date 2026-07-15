package biz.ugur.busroutebackend.catalogsearch.application.dto;

import biz.ugur.busroutebackend.catalogsearch.domain.model.AliasCollision;

public record CollisionResult(Long aliasId, String objectKind, String objectId, String title) {

    public static CollisionResult fromDomain(AliasCollision collision) {
        return new CollisionResult(collision.aliasId(), collision.objectKind().name(),
                collision.objectId(), collision.title());
    }
}
