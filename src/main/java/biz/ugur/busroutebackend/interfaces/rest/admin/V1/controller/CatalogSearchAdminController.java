package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.catalogsearch.application.dto.AliasCreatedResult;
import biz.ugur.busroutebackend.catalogsearch.application.dto.AliasList;
import biz.ugur.busroutebackend.catalogsearch.application.dto.AliasResult;
import biz.ugur.busroutebackend.catalogsearch.application.dto.CatalogSearchResult;
import biz.ugur.busroutebackend.catalogsearch.application.dto.CreateAliasCommand;
import biz.ugur.busroutebackend.catalogsearch.application.dto.PatchAliasCommand;
import biz.ugur.busroutebackend.catalogsearch.application.dto.RebuildResult;
import biz.ugur.busroutebackend.catalogsearch.application.usecase.CreateAliasUseCase;
import biz.ugur.busroutebackend.catalogsearch.application.usecase.DeleteAliasUseCase;
import biz.ugur.busroutebackend.catalogsearch.application.usecase.GetObjectAliasesUseCase;
import biz.ugur.busroutebackend.catalogsearch.application.usecase.PatchAliasUseCase;
import biz.ugur.busroutebackend.catalogsearch.application.usecase.PreviewCatalogSearchUseCase;
import biz.ugur.busroutebackend.catalogsearch.application.dto.NamesList;
import biz.ugur.busroutebackend.catalogsearch.application.usecase.RebuildCatalogSearchUseCase;
import biz.ugur.busroutebackend.catalogsearch.application.usecase.SearchAliasesUseCase;
import biz.ugur.busroutebackend.catalogsearch.application.usecase.SearchCatalogNamesUseCase;
import biz.ugur.busroutebackend.catalogsearch.domain.exceptions.CatalogSearchValidationException;
import biz.ugur.busroutebackend.shared.infrastructure.web.BasePaginatedController;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_CATALOG_SEARCH;

@RestController
@RequestMapping(V1_ADMIN_CATALOG_SEARCH)
public class CatalogSearchAdminController extends BasePaginatedController {

    private final CreateAliasUseCase createAliasUseCase;
    private final PatchAliasUseCase patchAliasUseCase;
    private final DeleteAliasUseCase deleteAliasUseCase;
    private final GetObjectAliasesUseCase getObjectAliasesUseCase;
    private final SearchAliasesUseCase searchAliasesUseCase;
    private final SearchCatalogNamesUseCase searchCatalogNamesUseCase;
    private final RebuildCatalogSearchUseCase rebuildUseCase;
    private final PreviewCatalogSearchUseCase previewUseCase;

    public CatalogSearchAdminController(CreateAliasUseCase createAliasUseCase,
                                        PatchAliasUseCase patchAliasUseCase,
                                        DeleteAliasUseCase deleteAliasUseCase,
                                        GetObjectAliasesUseCase getObjectAliasesUseCase,
                                        SearchAliasesUseCase searchAliasesUseCase,
                                        SearchCatalogNamesUseCase searchCatalogNamesUseCase,
                                        RebuildCatalogSearchUseCase rebuildUseCase,
                                        PreviewCatalogSearchUseCase previewUseCase,
                                        MessageSource messageSource) {
        super(messageSource);
        this.createAliasUseCase = createAliasUseCase;
        this.patchAliasUseCase = patchAliasUseCase;
        this.deleteAliasUseCase = deleteAliasUseCase;
        this.getObjectAliasesUseCase = getObjectAliasesUseCase;
        this.searchAliasesUseCase = searchAliasesUseCase;
        this.searchCatalogNamesUseCase = searchCatalogNamesUseCase;
        this.rebuildUseCase = rebuildUseCase;
        this.previewUseCase = previewUseCase;
    }

    public record CreateAliasRequest(String objectKind, String objectId, String aliasRaw,
                                     BigDecimal weight, String source) {
        CreateAliasCommand toCommand() {
            return new CreateAliasCommand(objectKind, objectId, aliasRaw, weight, source);
        }
    }

    public record PatchAliasRequest(BigDecimal weight, String source, String aliasRaw) {
        PatchAliasCommand toCommand(Long aliasId) {
            return new PatchAliasCommand(aliasId, weight, source, aliasRaw != null);
        }
    }

    @GetMapping("/aliases")
    public Mono<ResponseEntity<?>> listAliases(@RequestParam(required = false) String objectKind,
                                               @RequestParam(required = false) String objectId,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "25") int size) {
        boolean byObject = objectKind != null || objectId != null;
        if (byObject) {
            if (objectKind == null || objectId == null) {
                return Mono.error(new CatalogSearchValidationException("OBJECT_QUERY_INCOMPLETE",
                        "objectKind and objectId must be provided together"));
            }
            return getObjectAliasesUseCase
                    .execute(Mono.just(new GetObjectAliasesUseCase.Query(objectKind, objectId)))
                    .flatMap(list -> ok(Mono.just(list)))
                    .map(r -> r);
        }
        validatePagination(page, size);
        return searchAliasesUseCase
                .execute(Mono.just(new SearchAliasesUseCase.Query(q, page, size)))
                .flatMap(list -> okPaginated(Mono.just(list)))
                .map(r -> r);
    }

    @PostMapping("/aliases")
    public Mono<ResponseEntity<ApiResponse<AliasCreatedResult>>> createAlias(
            @RequestBody CreateAliasRequest request) {
        return created(Mono.just(request.toCommand()).as(createAliasUseCase::execute));
    }

    @PatchMapping("/aliases/{id}")
    public Mono<ResponseEntity<ApiResponse<AliasResult>>> patchAlias(@PathVariable Long id,
                                                                     @RequestBody PatchAliasRequest request) {
        return ok(Mono.just(request.toCommand(id)).as(patchAliasUseCase::execute));
    }

    @DeleteMapping("/aliases/{id}")
    public Mono<ResponseEntity<Void>> deleteAlias(@PathVariable Long id) {
        return deleteAliasUseCase.execute(Mono.just(id)).then(noContent());
    }

    @GetMapping("/names")
    public Mono<ResponseEntity<ApiResponse<NamesList>>> listNames(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int size) {
        validatePagination(page, size);
        return okPaginated(searchCatalogNamesUseCase
                .execute(Mono.just(new SearchCatalogNamesUseCase.Query(q, page, size))));
    }

    @PostMapping("/rebuild")
    public Mono<ResponseEntity<ApiResponse<RebuildResult>>> rebuild() {
        return ok(rebuildUseCase.execute(Mono.empty()));
    }

    @GetMapping("/preview")
    public Mono<ResponseEntity<ApiResponse<CatalogSearchResult>>> preview(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer limit) {
        return ok(previewUseCase.execute(Mono.just(new PreviewCatalogSearchUseCase.Query(q, limit))));
    }

    @Override
    protected String getControllerName() {
        return "CatalogSearchAdminController";
    }
}
