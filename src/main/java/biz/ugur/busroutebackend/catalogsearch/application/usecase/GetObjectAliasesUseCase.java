package biz.ugur.busroutebackend.catalogsearch.application.usecase;

import biz.ugur.busroutebackend.catalogsearch.application.dto.AliasResult;
import biz.ugur.busroutebackend.catalogsearch.domain.model.CatalogObjectKind;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.SearchAliasRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class GetObjectAliasesUseCase extends BaseUseCase<Mono<GetObjectAliasesUseCase.Query>, List<AliasResult>> {

    public record Query(String objectKind, String objectId) {
    }

    private final SearchAliasRepository aliasRepository;

    public GetObjectAliasesUseCase(SearchAliasRepository aliasRepository,
                                   CorrelationContextService correlationService,
                                   EventBus eventBus) {
        super(correlationService, eventBus);
        this.aliasRepository = aliasRepository;
    }

    @Override
    protected Mono<List<AliasResult>> process(Mono<Query> request) {
        return request.flatMap(query -> {
            CatalogObjectKind kind = AliasCommandValidator.requireKind(query.objectKind());
            String objectId = AliasCommandValidator.requireObjectId(query.objectId());
            return aliasRepository.findByObject(kind, objectId)
                    .map(AliasResult::fromDomain)
                    .collectList();
        });
    }

    @Override
    protected String getBoundContext() {
        return "catalogsearch";
    }
}
