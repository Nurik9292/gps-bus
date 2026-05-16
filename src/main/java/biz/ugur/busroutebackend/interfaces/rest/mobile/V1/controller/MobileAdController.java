package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.controller;

import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementResponse;
import biz.ugur.busroutebackend.advertising.application.dto.AdTariffResponse;
import biz.ugur.busroutebackend.advertising.application.dto.RecordClickCommand;
import biz.ugur.busroutebackend.advertising.application.dto.RecordDetailViewCommand;
import biz.ugur.busroutebackend.advertising.application.dto.RecordImpressionCommand;
import biz.ugur.busroutebackend.advertising.application.usecase.client.GetActiveAdsUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.client.GetActiveTariffsUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.client.RecordClickUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.client.RecordDetailViewUseCase;
import biz.ugur.busroutebackend.advertising.application.usecase.client.RecordImpressionUseCase;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementType;
import biz.ugur.busroutebackend.interfaces.rest.mobile.V1.request.RecordDetailViewRequest;
import biz.ugur.busroutebackend.interfaces.rest.mobile.V1.request.RecordEventRequest;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_MOBILE_ADS;


@Slf4j
@RestController
@RequestMapping(V1_MOBILE_ADS)
public class MobileAdController extends BaseController {

    private final GetActiveAdsUseCase getActiveAdsUseCase;
    private final GetActiveTariffsUseCase getActiveTariffsUseCase;
    private final RecordImpressionUseCase recordImpressionUseCase;
    private final RecordClickUseCase recordClickUseCase;
    private final RecordDetailViewUseCase recordDetailViewUseCase;

    public MobileAdController(GetActiveAdsUseCase getActiveAdsUseCase,
                               GetActiveTariffsUseCase getActiveTariffsUseCase,
                               RecordImpressionUseCase recordImpressionUseCase,
                               RecordClickUseCase recordClickUseCase,
                               RecordDetailViewUseCase recordDetailViewUseCase,
                               MessageSource messageSource) {
        super(messageSource);
        this.getActiveAdsUseCase = getActiveAdsUseCase;
        this.getActiveTariffsUseCase = getActiveTariffsUseCase;
        this.recordImpressionUseCase = recordImpressionUseCase;
        this.recordClickUseCase = recordClickUseCase;
        this.recordDetailViewUseCase = recordDetailViewUseCase;
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
    public Mono<ResponseEntity<Void>> recordImpression(
            @PathVariable String placementId,
            @RequestBody(required = false) RecordEventRequest request) {
        RecordEventRequest req = request != null ? request : new RecordEventRequest(null, null);
        req.validateTargetConsistency();

        recordImpressionUseCase
                .execute(new RecordImpressionCommand(placementId, req.targetType(), req.targetId()))
                .subscribe(
                        ok -> {},
                        err -> log.warn("Failed to record impression placement={}", placementId, err)
                );
        return Mono.just(ResponseEntity.noContent().build());
    }

    @PostMapping("/{placementId}/click")
    public Mono<ResponseEntity<Void>> recordClick(
            @PathVariable String placementId,
            @RequestBody(required = false) RecordEventRequest request) {
        RecordEventRequest req = request != null ? request : new RecordEventRequest(null, null);
        req.validateTargetConsistency();

        recordClickUseCase
                .execute(new RecordClickCommand(placementId, req.targetType(), req.targetId()))
                .subscribe(
                        ok -> {},
                        err -> log.warn("Failed to record click placement={}", placementId, err)
                );
        return Mono.just(ResponseEntity.noContent().build());
    }

    @PostMapping("/{placementId}/detail-view")
    public Mono<ResponseEntity<Void>> recordDetailView(
            @PathVariable String placementId,
            @Valid @RequestBody RecordDetailViewRequest request) {
        request.validateTargetConsistency();

        recordDetailViewUseCase
                .execute(new RecordDetailViewCommand(
                        placementId, request.durationMs(), request.targetType(), request.targetId()))
                .subscribe(
                        ok -> {},
                        err -> log.warn("Failed to record detail-view placement={}", placementId, err)
                );
        return Mono.just(ResponseEntity.noContent().build());
    }
}
