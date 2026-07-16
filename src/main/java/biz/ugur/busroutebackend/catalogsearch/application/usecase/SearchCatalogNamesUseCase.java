package biz.ugur.busroutebackend.catalogsearch.application.usecase;

import biz.ugur.busroutebackend.catalogsearch.application.dto.CatalogNameResult;
import biz.ugur.busroutebackend.catalogsearch.application.dto.NamesList;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.SearchAliasRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class SearchCatalogNamesUseCase extends BaseUseCase<Mono<SearchCatalogNamesUseCase.Query>, NamesList> {

    public record Query(String q, int page, int size) {
    }

    private final SearchAliasRepository aliasRepository;

    public SearchCatalogNamesUseCase(SearchAliasRepository aliasRepository,
                                     CorrelationContextService correlationService,
                                     EventBus eventBus) {
        super(correlationService, eventBus);
        this.aliasRepository = aliasRepository;
    }

    @Override
    protected Mono<NamesList> process(Mono<Query> request) {
        return request.flatMap(query -> aliasRepository.searchNames(query.q(), query.page(), query.size())
                .map(CatalogNameResult::fromDomain)
                .collectList()
                .zipWith(aliasRepository.countNames(query.q()))
                .map(t -> new NamesList(t.getT1(), query.page(), query.size(), t.getT2())));
    }

    @Override
    protected String getBoundContext() {
        return "catalogsearch";
    }
}
