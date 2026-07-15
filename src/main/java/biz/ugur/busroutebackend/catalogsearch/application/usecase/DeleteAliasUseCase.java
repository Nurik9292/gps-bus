package biz.ugur.busroutebackend.catalogsearch.application.usecase;

import biz.ugur.busroutebackend.catalogsearch.domain.exceptions.AliasNotFoundException;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogSearchIndexRepository;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.SearchAliasRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
public class DeleteAliasUseCase extends BaseUseCase<Mono<Long>, Void> {

    private final SearchAliasRepository aliasRepository;
    private final CatalogSearchIndexRepository indexRepository;

    public DeleteAliasUseCase(SearchAliasRepository aliasRepository,
                              CatalogSearchIndexRepository indexRepository,
                              CorrelationContextService correlationService,
                              EventBus eventBus) {
        super(correlationService, eventBus);
        this.aliasRepository = aliasRepository;
        this.indexRepository = indexRepository;
    }

    @Override
    @Transactional
    public Mono<Void> execute(Mono<Long> request) {
        return super.execute(request);
    }

    @Override
    protected Mono<Void> process(Mono<Long> request) {
        return request.flatMap(aliasId -> aliasRepository.findById(aliasId)
                .switchIfEmpty(Mono.error(new AliasNotFoundException(aliasId)))
                .flatMap(alias -> aliasRepository.deleteById(aliasId)
                        .then(indexRepository.rebuildObject(alias.getObjectKind(), alias.getObjectId()))
                        .then()));
    }

    @Override
    protected String getBoundContext() {
        return "catalogsearch";
    }
}
