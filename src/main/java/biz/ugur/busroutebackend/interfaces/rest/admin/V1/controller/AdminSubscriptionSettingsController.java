package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.shared.application.SecurityContextService;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import biz.ugur.busroutebackend.subscription.application.dto.SubscriptionPriceResponse;
import biz.ugur.busroutebackend.subscription.application.dto.UpdateSubscriptionPriceCommand;
import biz.ugur.busroutebackend.subscription.application.usecase.admin.GetSubscriptionPricesUseCase;
import biz.ugur.busroutebackend.subscription.application.usecase.admin.UpdateSubscriptionPriceUseCase;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_SUBSCRIPTIONS;

@RestController
@RequestMapping(V1_ADMIN_SUBSCRIPTIONS)
public class AdminSubscriptionSettingsController extends BaseController {

    private final GetSubscriptionPricesUseCase getPricesUseCase;
    private final UpdateSubscriptionPriceUseCase updatePriceUseCase;
    private final SecurityContextService securityContextService;

    public AdminSubscriptionSettingsController(GetSubscriptionPricesUseCase getPricesUseCase,
                                               UpdateSubscriptionPriceUseCase updatePriceUseCase,
                                               SecurityContextService securityContextService,
                                               MessageSource messageSource) {
        super(messageSource);
        this.getPricesUseCase = getPricesUseCase;
        this.updatePriceUseCase = updatePriceUseCase;
        this.securityContextService = securityContextService;
    }

    @Override
    protected String getControllerName() {
        return AdminSubscriptionSettingsController.class.getSimpleName();
    }

    @GetMapping("/prices")
    public Mono<ResponseEntity<ApiResponse<List<SubscriptionPriceResponse>>>> getPrices() {
        return ok(getPricesUseCase.execute(null));
    }

    @PutMapping("/prices/{period}")
    public Mono<ResponseEntity<ApiResponse<SubscriptionPriceResponse>>> updatePrice(
            @PathVariable String period,
            @Valid @RequestBody UpdatePriceRequest request) {
        return ok(securityContextService.getCurrentUsername()
                .flatMap(username -> updatePriceUseCase.execute(
                        new UpdateSubscriptionPriceCommand(period, request.amountMinor(), username))));
    }

    public record UpdatePriceRequest(@JsonProperty("amount_minor") @Positive long amountMinor) {}
}
