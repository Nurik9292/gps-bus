package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.controller;

import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementResponse;
import biz.ugur.busroutebackend.advertising.application.dto.AdTariffResponse;
import biz.ugur.busroutebackend.advertising.application.usecase.client.GetActiveAdsUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.client.GetActiveTariffsUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.client.RecordClickUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.client.RecordImpressionUseCase;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_MOBILE_ADS;


@RestController
@RequestMapping(V1_MOBILE_ADS)
@CrossOrigin(origins = "*")
public class MobileAdController extends BaseController {

    private final GetActiveAdsUseCase getActiveAdsUseCase;
    private final GetActiveTariffsUseCase getActiveTariffsUseCase;
    private final RecordImpressionUseCase recordImpressionUseCase;
    private final RecordClickUseCase recordClickUseCase;

    public MobileAdController(GetActiveAdsUseCase getActiveAdsUseCase,
                               GetActiveTariffsUseCase getActiveTariffsUseCase,
                               RecordImpressionUseCase recordImpressionUseCase,
                               RecordClickUseCase recordClickUseCase,
                               MessageSource messageSource) {
        super(messageSource);
        this.getActiveAdsUseCase = getActiveAdsUseCase;
        this.getActiveTariffsUseCase = getActiveTariffsUseCase;
        this.recordImpressionUseCase = recordImpressionUseCase;
        this.recordClickUseCase = recordClickUseCase;
    }

    @Override
    protected String getControllerName() {
        return MobileAdController.class.getSimpleName();
    }

    @GetMapping("/banner")
    public Mono<ResponseEntity<ApiResponse<List<AdPlacementResponse>>>> getBanners(
            @RequestParam(required = false) String context) {
        return ok(getActiveAdsUseCase.execute(new GetActiveAdsUseCase.Query(PlacementType.BANNER, context)));
    }

    @GetMapping("/popup")
    public Mono<ResponseEntity<ApiResponse<List<AdPlacementResponse>>>> getPopups(
            @RequestParam(required = false) String context) {
        return ok(getActiveAdsUseCase.execute(new GetActiveAdsUseCase.Query(PlacementType.POPUP, context)));
    }

    @GetMapping("/push")
    public Mono<ResponseEntity<ApiResponse<List<AdPlacementResponse>>>> getPushAds(
            @RequestParam(required = false) String context) {
        return ok(getActiveAdsUseCase.execute(new GetActiveAdsUseCase.Query(PlacementType.PUSH, context)));
    }

    @GetMapping("/tariffs")
    public Mono<ResponseEntity<ApiResponse<List<AdTariffResponse>>>> getTariffs(
            @RequestParam(value = "placement_type", required = false) String placementType) {
        return ok(getActiveTariffsUseCase.execute(placementType != null ? placementType : ""));
    }

    @PostMapping("/{placementId}/impression")
    public Mono<ResponseEntity<Void>> recordImpression(@PathVariable String placementId) {
        return recordImpressionUseCase.execute(placementId).then(noContent());
    }

    @PostMapping("/{placementId}/click")
    public Mono<ResponseEntity<Void>> recordClick(@PathVariable String placementId) {
        return recordClickUseCase.execute(placementId).then(noContent());
    }
}
