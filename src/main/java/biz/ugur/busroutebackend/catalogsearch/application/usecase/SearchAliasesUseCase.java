package biz.ugur.busroutebackend.catalogsearch.application.usecase;

import biz.ugur.busroutebackend.catalogsearch.application.dto.AliasList;
import biz.ugur.busroutebackend.catalogsearch.application.dto.AliasResult;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.SearchAliasRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class SearchAliasesUseCase extends BaseUseCase<Mono<SearchAliasesUseCase.Query>, AliasList> {

    public record Query(String q, int page, int size) {
    }

    private final SearchAliasRepository aliasRepository;

    public SearchAliasesUseCase(SearchAliasRepository aliasRepository,
                                CorrelationContextService correlationService,
                                EventBus eventBus) {
        super(correlationService, eventBus);
        this.aliasRepository = aliasRepository;
    }

    @Override
    protected Mono<AliasList> process(Mono<Query> request) {
        return request.flatMap(query -> {
            return aliasRepository.searchByText(query.q(), query.page(), query.size())
                    .map(AliasResult::fromDomain)
                    .collectList()
                    .zipWith(aliasRepository.countByText(query.q()))
                    .map(t -> new AliasList(t.getT1(), query.page(), query.size(), t.getT2()));
        });
    }

    @Override
    protected String getBoundContext() {
        return "catalogsearch";
    }
}
