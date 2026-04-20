package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.advertising.application.dto.AdTariffList;
import biz.ugur.busroutebackend.advertising.application.dto.AdTariffResponse;
import biz.ugur.busroutebackend.advertising.application.dto.CreateAdTariffCommand;
import biz.ugur.busroutebackend.advertising.application.dto.UpdateAdTariffCommand;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.CreateAdTariffUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.GetAdTariffByIdUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.GetAllAdTariffsUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.ToggleAdTariffStatusUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.UpdateAdTariffUseCase;
import biz.ugur.busroutebackend.shared.infrastructure.web.BasePaginatedController;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_AD_TARIFFS;

@RestController
@RequestMapping(V1_ADMIN_AD_TARIFFS)
public class AdminAdTariffController extends BasePaginatedController {

    private final CreateAdTariffUseCase createAdTariffUseCase;
    private final UpdateAdTariffUseCase updateAdTariffUseCase;
    private final GetAdTariffByIdUseCase getAdTariffByIdUseCase;
    private final GetAllAdTariffsUseCase getAllAdTariffsUseCase;
    private final ToggleAdTariffStatusUseCase toggleAdTariffStatusUseCase;

    public AdminAdTariffController(CreateAdTariffUseCase createAdTariffUseCase,
                                    UpdateAdTariffUseCase updateAdTariffUseCase,
                                    GetAdTariffByIdUseCase getAdTariffByIdUseCase,
                                    GetAllAdTariffsUseCase getAllAdTariffsUseCase,
                                    ToggleAdTariffStatusUseCase toggleAdTariffStatusUseCase,
                                    MessageSource messageSource) {
        super(messageSource);
        this.createAdTariffUseCase = createAdTariffUseCase;
        this.updateAdTariffUseCase = updateAdTariffUseCase;
        this.getAdTariffByIdUseCase = getAdTariffByIdUseCase;
        this.getAllAdTariffsUseCase = getAllAdTariffsUseCase;
        this.toggleAdTariffStatusUseCase = toggleAdTariffStatusUseCase;
    }

    @Override
    protected String getControllerName() {
        return AdminAdTariffController.class.getSimpleName();
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<AdTariffList>>> list(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "50") int size) {
        validatePagination(page, size);
        return okPaginated(getAllAdTariffsUseCase.execute(
                new GetAllAdTariffsUseCase.Query(page, size)));
    }

    @GetMapping("/{tariffId}")
    public Mono<ResponseEntity<ApiResponse<AdTariffResponse>>> getById(@PathVariable String tariffId) {
        return ok(getAdTariffByIdUseCase.execute(tariffId));
    }

    @PostMapping
    public Mono<ResponseEntity<ApiResponse<AdTariffResponse>>> create(
            @Valid @RequestBody CreateAdTariffCommand request) {
        return created(createAdTariffUseCase.execute(Mono.just(request)));
    }

    @PutMapping("/{tariffId}")
    public Mono<ResponseEntity<ApiResponse<AdTariffResponse>>> update(
            @PathVariable String tariffId,
            @Valid @RequestBody UpdateAdTariffCommand request) {
        return ok(updateAdTariffUseCase.execute(
                new UpdateAdTariffUseCase.Request(tariffId, request)));
    }

    @PostMapping("/{tariffId}/activate")
    public Mono<ResponseEntity<ApiResponse<AdTariffResponse>>> activate(@PathVariable String tariffId) {
        return ok(toggleAdTariffStatusUseCase.execute(
                new ToggleAdTariffStatusUseCase.Request(tariffId, true)));
    }

    @PostMapping("/{tariffId}/deactivate")
    public Mono<ResponseEntity<ApiResponse<AdTariffResponse>>> deactivate(@PathVariable String tariffId) {
        return ok(toggleAdTariffStatusUseCase.execute(
                new ToggleAdTariffStatusUseCase.Request(tariffId, false)));
    }
}
