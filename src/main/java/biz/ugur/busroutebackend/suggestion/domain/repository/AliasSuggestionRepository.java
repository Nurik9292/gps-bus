package biz.ugur.busroutebackend.suggestion.domain.repository;

import biz.ugur.busroutebackend.shared.base.BaseRepository;
import biz.ugur.busroutebackend.suggestion.domain.model.AliasSuggestion;
import biz.ugur.busroutebackend.suggestion.domain.model.SuggestionEntityType;
import biz.ugur.busroutebackend.suggestion.domain.model.SuggestionStatus;
import biz.ugur.busroutebackend.suggestion.domain.valueobjects.AliasSuggestionId;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AliasSuggestionRepository extends BaseRepository<AliasSuggestion, AliasSuggestionId> {

    Flux<AliasSuggestion> findByFilters(SuggestionStatus status, SuggestionEntityType entityType, Pageable pageable);

    Mono<Long> countByFilters(SuggestionStatus status, SuggestionEntityType entityType);

    Mono<Boolean> existsByEntityAndAlias(SuggestionEntityType entityType, String entityId, String suggestedAlias);

    Flux<AliasSuggestion> findByClientId(String clientId, Pageable pageable);

    Mono<Long> countByClientId(String clientId);
}
