package biz.ugur.busroutebackend.catalogsearch.infrastructure.scheduler;

import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogSearchIndexRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Component
public class CatalogSearchRebuildScheduler {

    private static final Duration TICK_TIMEOUT = Duration.ofMinutes(5);

    private final CatalogSearchIndexRepository indexRepository;

    public CatalogSearchRebuildScheduler(CatalogSearchIndexRepository indexRepository) {
        this.indexRepository = indexRepository;
    }

    @Scheduled(cron = "${ugur.catalog-search.rebuild-cron:0 0 4 * * *}",
               zone = "${ugur.catalog-search.rebuild-zone:Asia/Ashgabat}")
    public void nightlyRebuild() {
        rebuildTick()
                .doOnError(err -> log.error("[CATALOG_SEARCH] nightly rebuild failed", err))
                .onErrorResume(err -> Mono.empty())
                .subscribe(stats -> log.info(
                        "[CATALOG_SEARCH] nightly rebuild done: inserted={}, orphanAliases={}",
                        stats.inserted(), stats.orphanAliases()));
    }

    Mono<biz.ugur.busroutebackend.catalogsearch.domain.model.RebuildStats> rebuildTick() {
        return indexRepository.rebuildAll().timeout(TICK_TIMEOUT);
    }
}
