package biz.ugur.busroutebackend.transport.domain.repository;

import biz.ugur.busroutebackend.transport.domain.valueobject.NameChangeRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface NameHistoryRepository {

    Mono<Void> upsertAll(List<NameChangeRecord> changes);

    Flux<NameChangeRecord> findByEntity(String entityKind, String entityId);

    Mono<NameChangeRecord> findByEntityAndField(String entityKind, String entityId, String field);
}
