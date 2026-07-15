package biz.ugur.busroutebackend.catalogsearch.application.usecase;

import biz.ugur.busroutebackend.catalogsearch.application.dto.AliasCreatedResult;
import biz.ugur.busroutebackend.catalogsearch.application.dto.AliasResult;
import biz.ugur.busroutebackend.catalogsearch.application.dto.CollisionResult;
import biz.ugur.busroutebackend.catalogsearch.application.dto.CreateAliasCommand;
import biz.ugur.busroutebackend.catalogsearch.domain.exceptions.AliasAlreadyExistsException;
import biz.ugur.busroutebackend.catalogsearch.domain.exceptions.CatalogObjectNotFoundException;
import biz.ugur.busroutebackend.catalogsearch.domain.exceptions.CatalogSearchValidationException;
import biz.ugur.busroutebackend.catalogsearch.domain.model.AliasSource;
import biz.ugur.busroutebackend.catalogsearch.domain.model.CatalogObjectKind;
import biz.ugur.busroutebackend.catalogsearch.domain.model.SearchAlias;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogObjectLookup;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogSearchCache;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogSearchIndexRepository;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.SearchAliasRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.SecurityContextService;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Service
public class CreateAliasUseCase extends BaseUseCase<Mono<CreateAliasCommand>, AliasCreatedResult> {

    private final SearchAliasRepository aliasRepository;
    private final CatalogSearchIndexRepository indexRepository;
    private final CatalogObjectLookup objectLookup;
    private final SecurityContextService securityContextService;
    private final CatalogSearchCache cache;
    private final TransactionalOperator transactionalOperator;

    public CreateAliasUseCase(SearchAliasRepository aliasRepository,
                              CatalogSearchIndexRepository indexRepository,
                              CatalogObjectLookup objectLookup,
                              SecurityContextService securityContextService,
                              CatalogSearchCache cache,
                              TransactionalOperator transactionalOperator,
                              CorrelationContextService correlationService,
                              EventBus eventBus) {
        super(correlationService, eventBus);
        this.aliasRepository = aliasRepository;
        this.indexRepository = indexRepository;
        this.objectLookup = objectLookup;
        this.securityContextService = securityContextService;
        this.cache = cache;
        this.transactionalOperator = transactionalOperator;
    }

    @Override
    protected Mono<AliasCreatedResult> process(Mono<CreateAliasCommand> request) {
        return request.flatMap(cmd -> {
            CatalogObjectKind kind = AliasCommandValidator.requireKind(cmd.objectKind());
            String objectId = AliasCommandValidator.requireObjectId(cmd.objectId());
            String aliasRaw = AliasCommandValidator.requireAliasRaw(cmd.aliasRaw());
            BigDecimal weight = AliasCommandValidator.requireWeight(cmd.weight());
            AliasSource source = AliasCommandValidator.requireApiSource(cmd.source(), AliasSource.CURATED);

            return objectLookup.findTitle(kind, objectId)
                    .switchIfEmpty(Mono.error(new CatalogObjectNotFoundException(kind, objectId)))
                    .flatMap(title -> aliasRepository.normalize(aliasRaw)
                            .flatMap(norm -> {
                                if (norm.isBlank()) {
                                    return Mono.error(new CatalogSearchValidationException(
                                            "ALIAS_NORMALIZES_TO_EMPTY",
                                            "aliasRaw normalizes to empty string: " + aliasRaw));
                                }
                                return createWithReindex(kind, objectId, aliasRaw, norm, weight, source, title)
                                        .flatMap(result -> cache.evictAll().thenReturn(result));
                            }));
        });
    }

    private Mono<AliasCreatedResult> createWithReindex(CatalogObjectKind kind, String objectId,
                                                       String aliasRaw, String norm, BigDecimal weight,
                                                       AliasSource source, String title) {
        return transactionalOperator.transactional(
                aliasRepository.existsByObjectAndRaw(kind, objectId, aliasRaw)
                .flatMap(exists -> exists
                        ? Mono.error(new AliasAlreadyExistsException(kind, objectId, aliasRaw))
                        : securityContextService.getCurrentAdminId()
                                .defaultIfEmpty("")
                                .map(adminId -> SearchAlias.create(kind, objectId, aliasRaw, norm,
                                        weight, source, adminId.isBlank() ? null : adminId)))
                .flatMap(aliasRepository::save)
                .onErrorMap(DuplicateKeyException.class,
                        e -> new AliasAlreadyExistsException(kind, objectId, aliasRaw))
                .flatMap(saved -> indexRepository.rebuildObject(kind, objectId).thenReturn(saved))
                .flatMap(saved -> aliasRepository
                        .findCollisions(saved.getAliasNorm(), kind, objectId)
                        .map(CollisionResult::fromDomain)
                        .collectList()
                        .map(collisions -> new AliasCreatedResult(
                                AliasResult.fromDomain(saved, title), collisions))));
    }

    @Override
    protected String getBoundContext() {
        return "catalogsearch";
    }
}
