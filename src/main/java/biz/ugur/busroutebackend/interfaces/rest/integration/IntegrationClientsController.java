package biz.ugur.busroutebackend.interfaces.rest.integration;

import biz.ugur.busroutebackend.integration.application.dto.*;
import biz.ugur.busroutebackend.integration.application.usecase.GetIntegrationClientTokenUseCase;
import biz.ugur.busroutebackend.integration.application.usecase.GetIntegrationClientsUseCase;
import biz.ugur.busroutebackend.integration.application.usecase.RegisterIntegrationClientUseCase;
import biz.ugur.busroutebackend.integration.infrastructure.security.ApiTokenPrincipal;
import biz.ugur.busroutebackend.shared.infrastructure.web.BasePaginatedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_INTEGRATION_CLIENTS;

@RestController
@RequestMapping(V1_INTEGRATION_CLIENTS)
@Tag(name = "Integration Clients", description = "Manage clients via external service API")
public class IntegrationClientsController extends BasePaginatedController {

    private final RegisterIntegrationClientUseCase registerClientUseCase;
    private final GetIntegrationClientTokenUseCase getTokenUseCase;
    private final GetIntegrationClientsUseCase getClientsUseCase;

    public IntegrationClientsController(RegisterIntegrationClientUseCase registerClientUseCase,
                                        GetIntegrationClientTokenUseCase getTokenUseCase,
                                        GetIntegrationClientsUseCase getClientsUseCase,
                                        MessageSource messageSource) {
        super(messageSource);
        this.registerClientUseCase = registerClientUseCase;
        this.getTokenUseCase = getTokenUseCase;
        this.getClientsUseCase = getClientsUseCase;
    }

    @Override
    protected String getControllerName() {
        return IntegrationClientsController.class.getSimpleName();
    }


    @PostMapping
    @Operation(summary = "Register new client via external service")
    public Mono<ResponseEntity<ApiResponse<IntegrationClientDTO>>> registerClient(
            @Valid @RequestBody RegisterIntegrationClientRequest request) {

        return getServiceId()
                .flatMap(serviceId -> registerClientUseCase.execute(serviceId, request))
                .flatMap(dto -> created(Mono.just(dto)));
    }


    @PostMapping("/token")
    @Operation(summary = "Get JWT token for a client")
    public Mono<ResponseEntity<ApiResponse<IntegrationClientTokenResponse>>> getClientToken(
            @Valid @RequestBody IntegrationClientTokenRequest request) {

        return getServiceId()
                .flatMap(serviceId -> getTokenUseCase.execute(serviceId, request))
                .flatMap(dto -> ok(Mono.just(dto)));
    }

 
    @PostMapping("/{clientId}/token")
    @Operation(summary = "Get JWT token for a specific client by ID")
    public Mono<ResponseEntity<ApiResponse<IntegrationClientTokenResponse>>> getClientTokenById(
            @PathVariable String clientId) {

        IntegrationClientTokenRequest request = new IntegrationClientTokenRequest(clientId, null);

        return getServiceId()
                .flatMap(serviceId -> getTokenUseCase.execute(serviceId, request))
                .flatMap(dto -> ok(Mono.just(dto)));
    }

  
    @GetMapping
    @Operation(summary = "List all clients created by this service")
    public Mono<ResponseEntity<ApiResponse<IntegrationClientsPagedList>>> getClients(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        validatePagination(page, size);

        return getServiceId()
                .flatMap(serviceId -> getClientsUseCase.execute(serviceId, page, size))
                .flatMap(pagedList -> okPaginated(Mono.just(pagedList)));
    }

    private Mono<String> getServiceId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(auth -> (ApiTokenPrincipal) auth.getPrincipal())
                .map(ApiTokenPrincipal::getExternalServiceId);
    }
}
