package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.controller;

import biz.ugur.busroutebackend.advertising.application.usecase.mobile.GetActiveBannersAsAdPlacementsUseCase;
import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.banner.application.dto.BannerList;
import biz.ugur.busroutebackend.banner.application.dto.BannerPaginationQuery;
import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.banner.domain.exceptions.BannerValidationException;
import biz.ugur.busroutebackend.client.application.usecase.RouteIsFavoriteUseCase;
import biz.ugur.busroutebackend.shared.utility.BannerTypeTargetTypeMapper;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.accept.RequestedContentTypeResolver;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_MOBILE_BANNERS;

@RestController
@RequestMapping(V1_MOBILE_BANNERS)
public class MobileBannerApiController extends BaseMobileController {

    private final GetActiveBannersAsAdPlacementsUseCase getActiveBannersUseCase;

    public MobileBannerApiController(MessageSource messageSource,
                                     RequestedContentTypeResolver requestedContentTypeResolver,
                                     RouteIsFavoriteUseCase routeIsFavoriteUseCase,
                                     GetActiveBannersAsAdPlacementsUseCase getActiveBannersUseCase) {
        super(messageSource, requestedContentTypeResolver, routeIsFavoriteUseCase);
        this.getActiveBannersUseCase = getActiveBannersUseCase;
    }

    @Override
    protected String getControllerName() {
        return MobileBannerApiController.class.getSimpleName();
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<BannerList>>> getAllBanners() {
        return ok(getActiveBannersUseCase.executeAll());
    }

    @GetMapping("/paginated")
    public Mono<ResponseEntity<ApiResponse<BannerList>>> getBannersPaginated(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "displayOrder") String sortField,
            @RequestParam(defaultValue = "asc") String sortOrder,
            @RequestParam(defaultValue = "main") String type) {

        validatePagination(page, size);

        BannerPaginationQuery query = BannerPaginationQuery.createWithType(
                page,
                size,
                camelToSnake(sortField),
                sortOrder,
                true,
                type);

        return okPaginated(getActiveBannersUseCase.executePaginated(query));
    }

    @GetMapping("/type/{type}")
    public Mono<ResponseEntity<ApiResponse<BannerList>>> getBannersByType(@PathVariable String type) {
        return Mono.defer(() -> {
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
