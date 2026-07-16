package biz.ugur.busroutebackend.catalogsearch.application.dto;

import java.util.List;

public record AliasCreatedResult(AliasResult alias, List<CollisionResult> collisions) {
}
