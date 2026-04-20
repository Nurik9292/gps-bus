package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.admin.infrastructure.security.AdminPrincipal;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.SuggestionRejectRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.SuggestionListResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.SuggestionResponse;
import biz.ugur.busroutebackend.suggestion.application.dto.GetAllSuggestionsInput;
import biz.ugur.busroutebackend.suggestion.application.dto.ReviewSuggestionInput;
import biz.ugur.busroutebackend.suggestion.application.usecase.ApproveSuggestionUseCase;
import biz.ugur.busroutebackend.suggestion.application.usecase.GetAllSuggestionsUseCase;
import biz.ugur.busroutebackend.suggestion.application.usecase.RejectSuggestionUseCase;
import biz.ugur.busroutebackend.shared.infrastructure.web.BasePaginatedController;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_SUGGESTIONS;

@RestController
@RequestMapping(V1_ADMIN_SUGGESTIONS)
public class AdminSuggestionController extends BasePaginatedController {

    private final GetAllSuggestionsUseCase getAllSuggestionsUseCase;
    private final ApproveSuggestionUseCase approveSuggestionUseCase;
    private final RejectSuggestionUseCase rejectSuggestionUseCase;

    public AdminSuggestionController(GetAllSuggestionsUseCase getAllSuggestionsUseCase,
                                      ApproveSuggestionUseCase approveSuggestionUseCase,
                                      RejectSuggestionUseCase rejectSuggestionUseCase,
                                      MessageSource messageSource) {
        super(messageSource);
        this.getAllSuggestionsUseCase = getAllSuggestionsUseCase;
        this.approveSuggestionUseCase = approveSuggestionUseCase;
        this.rejectSuggestionUseCase = rejectSuggestionUseCase;
    }

    @Override
    protected String getControllerName() {
        return AdminSuggestionController.class.getSimpleName();
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<SuggestionListResponse>>> getAllSuggestions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "created_at") String sort,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String entityType) {

        validatePagination(page, size);

        return ok(Mono.just(GetAllSuggestionsInput.fromParams(page, size, camelToSnake(sort), order, status, entityType))
                .as(getAllSuggestionsUseCase::execute)
                .map(SuggestionListResponse::fromResult));
    }

    @PatchMapping("/{id}/approve")
    public Mono<ResponseEntity<ApiResponse<SuggestionResponse>>> approveSuggestion(@PathVariable String id) {
        return ok(getCurrentAdminId().flatMap(adminId -> {
            ReviewSuggestionInput input = new ReviewSuggestionInput(id, adminId, null);
            return Mono.just(input)
                    .as(approveSuggestionUseCase::execute)
                    .map(SuggestionResponse::fromResult);
        }));
    }

    @PatchMapping("/{id}/reject")
    public Mono<ResponseEntity<ApiResponse<SuggestionResponse>>> rejectSuggestion(
            @PathVariable String id,
            @RequestBody(required = false) SuggestionRejectRequest request) {
        return ok(getCurrentAdminId().flatMap(adminId -> {
            String comment = request != null ? request.getComment() : null;
            ReviewSuggestionInput input = new ReviewSuggestionInput(id, adminId, comment);
            return Mono.just(input)
                    .as(rejectSuggestionUseCase::execute)
                    .map(SuggestionResponse::fromResult);
        }));
    }

    private Mono<String> getCurrentAdminId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(auth -> (AdminPrincipal) auth.getPrincipal())
                .map(principal -> principal.getId().getValue());
    }
}
