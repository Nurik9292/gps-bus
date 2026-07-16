package biz.ugur.busroutebackend.catalogsearch.application.usecase;

import biz.ugur.busroutebackend.catalogsearch.domain.exceptions.AliasNotFoundException;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogSearchCache;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogSearchIndexRepository;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.SearchAliasRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

@Service
public class DeleteAliasUseCase extends BaseUseCase<Mono<Long>, Void> {

    private final SearchAliasRepository aliasRepository;
    private final CatalogSearchIndexRepository indexRepository;
    private final CatalogSearchCache cache;
    private final TransactionalOperator transactionalOperator;

    public DeleteAliasUseCase(SearchAliasRepository aliasRepository,
                              CatalogSearchIndexRepository indexRepository,
                              CatalogSearchCache cache,
                              TransactionalOperator transactionalOperator,
                              CorrelationContextService correlationService,
                              EventBus eventBus) {
        super(correlationService, eventBus);
        this.aliasRepository = aliasRepository;
        this.indexRepository = indexRepository;
        this.cache = cache;
        this.transactionalOperator = transactionalOperator;
    }

    @Override
    protected Mono<Void> process(Mono<Long> request) {
        return request.flatMap(aliasId -> transactionalOperator.transactional(
                        aliasRepository.findById(aliasId)
                                .switchIfEmpty(Mono.error(new AliasNotFoundException(aliasId)))
                                .flatMap(alias -> aliasRepository.deleteById(aliasId)
                                        .then(indexRepository.rebuildObject(
                                                alias.getObjectKind(), alias.getObjectId()))))
                .then(cache.evictAll())
                .then());
    }

    @Override
    protected String getBoundContext() {
        return "catalogsearch";
    }
}
