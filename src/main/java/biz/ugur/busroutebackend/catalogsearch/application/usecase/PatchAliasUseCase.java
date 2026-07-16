package biz.ugur.busroutebackend.catalogsearch.application.usecase;

import biz.ugur.busroutebackend.catalogsearch.application.dto.AliasResult;
import biz.ugur.busroutebackend.catalogsearch.application.dto.PatchAliasCommand;
import biz.ugur.busroutebackend.catalogsearch.domain.exceptions.AliasNotFoundException;
import biz.ugur.busroutebackend.catalogsearch.domain.exceptions.CatalogSearchValidationException;
import biz.ugur.busroutebackend.catalogsearch.domain.model.AliasSource;
import biz.ugur.busroutebackend.catalogsearch.domain.model.SearchAlias;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogObjectLookup;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogSearchCache;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogSearchIndexRepository;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.SearchAliasRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Service
public class PatchAliasUseCase extends BaseUseCase<Mono<PatchAliasCommand>, AliasResult> {

    private final SearchAliasRepository aliasRepository;
    private final CatalogSearchIndexRepository indexRepository;
    private final CatalogObjectLookup objectLookup;
    private final CatalogSearchCache cache;
    private final TransactionalOperator transactionalOperator;

    public PatchAliasUseCase(SearchAliasRepository aliasRepository,
                             CatalogSearchIndexRepository indexRepository,
                             CatalogObjectLookup objectLookup,
                             CatalogSearchCache cache,
                             TransactionalOperator transactionalOperator,
                             CorrelationContextService correlationService,
                             EventBus eventBus) {
        super(correlationService, eventBus);
        this.aliasRepository = aliasRepository;
        this.indexRepository = indexRepository;
        this.objectLookup = objectLookup;
        this.cache = cache;
        this.transactionalOperator = transactionalOperator;
    }

    @Override
    protected Mono<AliasResult> process(Mono<PatchAliasCommand> request) {
        return request.flatMap(cmd -> {
            if (cmd.aliasRawPresent()) {
                throw new CatalogSearchValidationException("ALIAS_RAW_IMMUTABLE",
                        "aliasRaw is immutable: delete and create a new alias instead");
            }
            BigDecimal weight = cmd.weight() == null ? null
                    : AliasCommandValidator.requireWeight(cmd.weight());
            AliasSource source = cmd.source() == null ? null
                    : AliasCommandValidator.requireApiSource(cmd.source(), null);

            return transactionalOperator.transactional(
                            aliasRepository.findById(cmd.aliasId())
                                    .switchIfEmpty(Mono.error(new AliasNotFoundException(cmd.aliasId())))
                                    .map(alias -> alias.withCuration(weight, source))
                                    .flatMap(aliasRepository::save)
                                    .flatMap(saved -> indexRepository
                                            .rebuildObject(saved.getObjectKind(), saved.getObjectId())
                                            .thenReturn(saved)))
                    .flatMap(saved -> cache.evictAll().thenReturn(saved))
                    .flatMap(this::toResult);
        });
    }

    private Mono<AliasResult> toResult(SearchAlias saved) {
        return objectLookup.findTitle(saved.getObjectKind(), saved.getObjectId())
                .defaultIfEmpty("")
                .map(title -> AliasResult.fromDomain(saved, title.isBlank() ? null : title));
    }

    @Override
    protected String getBoundContext() {
        return "catalogsearch";
    }
}
