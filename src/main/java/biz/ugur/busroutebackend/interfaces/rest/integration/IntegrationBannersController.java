package biz.ugur.busroutebackend.interfaces.rest.integration;

import biz.ugur.busroutebackend.advertising.application.dto.integration.ExternalBannerCommand;
import biz.ugur.busroutebackend.advertising.application.usecase.integration.UpsertExternalBannerUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.integration.WithdrawExternalBannerUseCase;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.integration.infrastructure.security.ApiTokenPrincipal;
import biz.ugur.busroutebackend.interfaces.rest.integration.request.ExternalBannerRequest;
import biz.ugur.busroutebackend.shared.infrastructure.web.BasePaginatedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_INTEGRATION_BANNERS;

@RestController
@RequestMapping(V1_INTEGRATION_BANNERS)
@Tag(name = "Integration Banners", description = "Приём баннеров от внешнего сервиса")
public class IntegrationBannersController extends BasePaginatedController {

    private final UpsertExternalBannerUseCase upsertUseCase;
    private final WithdrawExternalBannerUseCase withdrawUseCase;

    public IntegrationBannersController(UpsertExternalBannerUseCase upsertUseCase,
                                        WithdrawExternalBannerUseCase withdrawUseCase,
                                        MessageSource messageSource) {
        super(messageSource);
        this.upsertUseCase = upsertUseCase;
        this.withdrawUseCase = withdrawUseCase;
    }

    @Override
    protected String getControllerName() {
        return IntegrationBannersController.class.getSimpleName();
    }

    @PostMapping
    @Operation(summary = "Передать баннер: создание либо обновление по external_ref")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> upsert(
            @Valid @RequestBody ExternalBannerRequest request) {
        return ok(serviceId()
                .map(serviceId -> new ExternalBannerCommand(serviceId, request.externalRef(),
                        request.type(), request.title(), request.imageUrl(), request.targetUrl(),
                        request.content(), request.startsAt(), request.endsAt(), request.displayOrder()))
                .as(upsertUseCase::execute)
                .map(IntegrationBannersController::describe));
    }

    @DeleteMapping("/{externalRef}")
    @Operation(summary = "Снять баннер с показа")
    public Mono<ResponseEntity<ApiResponse<Map<String, Object>>>> withdraw(@PathVariable String externalRef) {
        return ok(serviceId()
                .map(serviceId -> new WithdrawExternalBannerUseCase.Command(serviceId, externalRef))
                .as(withdrawUseCase::execute)
                .map(IntegrationBannersController::describe));
    }

    private static Map<String, Object> describe(AdPlacement placement) {
        return Map.of(
                "id", placement.getId().getValue(),
                "external_ref", placement.getExternalRef(),
                "title", placement.getTitle(),
                "status", placement.getStatus().name(),
                "display_order", placement.getDisplayOrder());
    }

    private Mono<String> serviceId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(auth -> (ApiTokenPrincipal) auth.getPrincipal())
                .map(ApiTokenPrincipal::getExternalServiceId);
    }
}
