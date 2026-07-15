package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.controller;

import biz.ugur.busroutebackend.catalogsearch.application.dto.CatalogSearchResult;
import biz.ugur.busroutebackend.catalogsearch.application.usecase.SearchCatalogUseCase;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_MOBILE_SEARCH;

@RestController
@RequestMapping(V1_MOBILE_SEARCH)
@ConditionalOnProperty(name = "ugur.catalog-search.enabled", havingValue = "true")
public class MobileSearchController extends BaseController {

    private final SearchCatalogUseCase searchCatalogUseCase;

    public MobileSearchController(SearchCatalogUseCase searchCatalogUseCase,
                                  MessageSource messageSource) {
        super(messageSource);
        this.searchCatalogUseCase = searchCatalogUseCase;
    }

    @GetMapping
    @RateLimiter(name = "searchApi")
    public Mono<ResponseEntity<ApiResponse<CatalogSearchResult>>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon) {
        return ok(searchCatalogUseCase.execute(Mono.just(new SearchCatalogUseCase.Query(q, limit, lat, lon))));
    }

    @Override
    protected String getControllerName() {
        return "MobileSearchController";
    }
}
