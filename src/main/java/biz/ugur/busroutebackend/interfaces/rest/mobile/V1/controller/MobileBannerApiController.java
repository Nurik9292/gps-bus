package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.controller;

import biz.ugur.busroutebackend.advertising.application.usecase.mobile.GetActiveBannersAsAdPlacementsUseCase;
import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.banner.domain.exceptions.BannerValidationException;
import biz.ugur.busroutebackend.interfaces.rest.mobile.V1.dto.BannerResponse;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import biz.ugur.busroutebackend.shared.utility.BannerTypeTargetTypeMapper;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_MOBILE_BANNERS;

@RestController
@RequestMapping(V1_MOBILE_BANNERS)
public class MobileBannerApiController extends BaseController {

    private final GetActiveBannersAsAdPlacementsUseCase getActiveBannersUseCase;

    public MobileBannerApiController(MessageSource messageSource,
                                     GetActiveBannersAsAdPlacementsUseCase getActiveBannersUseCase) {
        super(messageSource);
        this.getActiveBannersUseCase = getActiveBannersUseCase;
    }

    @Override
    protected String getControllerName() {
        return MobileBannerApiController.class.getSimpleName();
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<List<BannerResponse>>>> getBanners(
            @RequestParam(name = "type", required = false) String type) {
        return Mono.defer(() -> {
            if (type == null || type.isBlank()) {
                return Mono.error(new BannerValidationException("type query parameter is required"));
            }
            BannerType bannerType = BannerType.fromValue(type);
            if (bannerType == BannerType.STOP_BUTTON) {
                return Mono.error(new BannerValidationException(
                        "Banner type 'stop-button' is no longer supported"));
            }
            TargetType targetType = BannerTypeTargetTypeMapper.toTarget(bannerType);
            return ok(getActiveBannersUseCase.execute(targetType));
        });
    }
}
