package biz.ugur.busroutebackend.catalogsearch.application.usecase;

import biz.ugur.busroutebackend.catalogsearch.application.dto.RebuildResult;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogSearchIndexRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class RebuildCatalogSearchUseCase extends BaseUseCase<Mono<Void>, RebuildResult> {

    private final CatalogSearchIndexRepository indexRepository;

    public RebuildCatalogSearchUseCase(CatalogSearchIndexRepository indexRepository,
                                       CorrelationContextService correlationService,
                                       EventBus eventBus) {
        super(correlationService, eventBus);
        this.indexRepository = indexRepository;
    }

    @Override
    protected Mono<RebuildResult> process(Mono<Void> request) {
        return Mono.defer(() -> {
            long startedAt = System.currentTimeMillis();
            return indexRepository.rebuildAll()
                    .map(stats -> new RebuildResult(stats.inserted(), stats.orphanAliases(),
                            System.currentTimeMillis() - startedAt));
        });
    }

    @Override
    protected String getBoundContext() {
        return "catalogsearch";
    }
}
