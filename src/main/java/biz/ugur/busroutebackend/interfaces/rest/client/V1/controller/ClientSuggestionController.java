package biz.ugur.busroutebackend.interfaces.rest.client.V1.controller;

import biz.ugur.busroutebackend.client.infrastructure.security.ClientPrincipal;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.SuggestionListResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.SuggestionResponse;
import biz.ugur.busroutebackend.interfaces.rest.client.V1.request.SuggestionCreateRequest;
import biz.ugur.busroutebackend.suggestion.application.dto.CreateSuggestionInput;
import biz.ugur.busroutebackend.suggestion.application.dto.GetClientSuggestionsInput;
import biz.ugur.busroutebackend.suggestion.application.usecase.CreateAliasSuggestionUseCase;
import biz.ugur.busroutebackend.suggestion.application.usecase.GetClientSuggestionsUseCase;
import biz.ugur.busroutebackend.suggestion.domain.model.SuggestionEntityType;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_CLIENT_SUGGESTIONS;

@RestController
@RequestMapping(V1_CLIENT_SUGGESTIONS)
@CrossOrigin(origins = "*")
public class ClientSuggestionController extends BaseController {

    private final CreateAliasSuggestionUseCase createAliasSuggestionUseCase;
    private final GetClientSuggestionsUseCase getClientSuggestionsUseCase;

    public ClientSuggestionController(CreateAliasSuggestionUseCase createAliasSuggestionUseCase,
                                       GetClientSuggestionsUseCase getClientSuggestionsUseCase,
                                       MessageSource messageSource) {
        super(messageSource);
        this.createAliasSuggestionUseCase = createAliasSuggestionUseCase;
        this.getClientSuggestionsUseCase = getClientSuggestionsUseCase;
    }

    @Override
    protected String getControllerName() {
        return ClientSuggestionController.class.getSimpleName();
    }

    @PostMapping
    public Mono<ResponseEntity<ApiResponse<SuggestionResponse>>> createSuggestion(
            @Valid @RequestBody SuggestionCreateRequest request) {
        return created(getCurrentClientId().flatMap(clientId -> {
            CreateSuggestionInput input = new CreateSuggestionInput(
                    SuggestionEntityType.valueOf(request.getEntityType().toUpperCase()),
                    request.getEntityId(),
                    request.getSuggestedAlias(),
                    request.getLanguage(),
                    clientId
            );
            return Mono.just(input)
                    .as(createAliasSuggestionUseCase::execute)
                    .map(SuggestionResponse::fromResult);
        }));
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<SuggestionListResponse>>> getMySuggestions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "created_at") String sort,
            @RequestParam(defaultValue = "desc") String order) {
        return ok(getCurrentClientId().flatMap(clientId -> {
            GetClientSuggestionsInput input = GetClientSuggestionsInput.fromParams(
                    clientId, page, size, camelToSnake(sort), order);
            return Mono.just(input)
                    .as(getClientSuggestionsUseCase::execute)
                    .map(SuggestionListResponse::fromResult);
        }));
    }

    private Mono<String> getCurrentClientId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(auth -> (ClientPrincipal) auth.getPrincipal())
                .map(ClientPrincipal::getClientId);
    }
}
