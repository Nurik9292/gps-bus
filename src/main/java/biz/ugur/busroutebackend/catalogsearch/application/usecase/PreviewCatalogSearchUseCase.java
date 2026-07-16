package biz.ugur.busroutebackend.catalogsearch.application.usecase;

import biz.ugur.busroutebackend.catalogsearch.application.dto.CatalogSearchResult;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class PreviewCatalogSearchUseCase extends BaseUseCase<Mono<PreviewCatalogSearchUseCase.Query>, CatalogSearchResult> {

    public record Query(String q, Integer limit) {
    }

    private final SearchCatalogUseCase searchCatalogUseCase;

    public PreviewCatalogSearchUseCase(SearchCatalogUseCase searchCatalogUseCase,
                                       CorrelationContextService correlationService,
                                       EventBus eventBus) {
        super(correlationService, eventBus);
        this.searchCatalogUseCase = searchCatalogUseCase;
    }

    @Override
    protected Mono<CatalogSearchResult> process(Mono<Query> request) {
        return request.flatMap(query -> searchCatalogUseCase.execute(Mono.just(
                new SearchCatalogUseCase.Query(query.q(), query.limit(), null, null, true))));
    }

    @Override
    protected String getBoundContext() {
        return "catalogsearch";
    }
}
