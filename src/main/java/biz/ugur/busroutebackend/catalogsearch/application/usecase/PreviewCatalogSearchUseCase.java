package biz.ugur.busroutebackend.catalogsearch.application.usecase;

import biz.ugur.busroutebackend.catalogsearch.application.dto.CatalogSearchResult;
import biz.ugur.busroutebackend.catalogsearch.application.dto.SearchHitResult;
import biz.ugur.busroutebackend.catalogsearch.domain.exceptions.CatalogSearchValidationException;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogSearchIndexRepository;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.SearchAliasRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class PreviewCatalogSearchUseCase extends BaseUseCase<Mono<PreviewCatalogSearchUseCase.Query>, CatalogSearchResult> {

    public record Query(String q, Integer limit) {
    }

    static final int LIMIT_DEFAULT = 10;
    static final int LIMIT_MAX = 50;

    private final CatalogSearchIndexRepository indexRepository;
    private final SearchAliasRepository aliasRepository;

    public PreviewCatalogSearchUseCase(CatalogSearchIndexRepository indexRepository,
                                       SearchAliasRepository aliasRepository,
                                       CorrelationContextService correlationService,
                                       EventBus eventBus) {
        super(correlationService, eventBus);
        this.indexRepository = indexRepository;
        this.aliasRepository = aliasRepository;
    }

    @Override
    protected Mono<CatalogSearchResult> process(Mono<Query> request) {
        return request.flatMap(query -> {
            int limit = resolveLimit(query.limit());
            String q = query.q() == null ? "" : query.q();
            return aliasRepository.normalize(q)
                    .flatMap(qn -> qn.isBlank()
                            ? Mono.just(new CatalogSearchResult(q, true, List.of()))
                            : indexRepository.search(qn, limit)
                                    .map(SearchHitResult::fromDomain)
                                    .collectList()
                                    .map(items -> new CatalogSearchResult(q, true, items)));
        });
    }

    private int resolveLimit(Integer raw) {
        int limit = raw == null ? LIMIT_DEFAULT : raw;
        if (limit < 1 || limit > LIMIT_MAX) {
            throw new CatalogSearchValidationException("LIMIT_OUT_OF_RANGE",
                    "limit must be within [1, " + LIMIT_MAX + "]: " + limit);
        }
        return limit;
    }

    @Override
    protected String getBoundContext() {
        return "catalogsearch";
    }
}
