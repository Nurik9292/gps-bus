package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.controller;

import biz.ugur.busroutebackend.banner.application.dto.BannerList;
import biz.ugur.busroutebackend.banner.application.dto.BannerPaginationQuery;
import biz.ugur.busroutebackend.banner.application.usecase.admin.GetAllBannersUseCase;
import biz.ugur.busroutebackend.banner.application.usecase.admin.GetBannersByTypeUseCase;
import biz.ugur.busroutebackend.banner.application.usecase.client.GetBannersWithPaginationByTypeUseCase;
import biz.ugur.busroutebackend.client.application.usecase.RouteIsFavoriteUseCase;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.accept.RequestedContentTypeResolver;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_MOBILE_BANNERS;

@RestController
@RequestMapping(V1_MOBILE_BANNERS)
public class MobileBannerApiController extends BaseMobileController {

    private final GetAllBannersUseCase getAllBannersUseCase;
    private final GetBannersWithPaginationByTypeUseCase getBannersWithPaginationUseCase;
    private final GetBannersByTypeUseCase getBannersByTypeUseCase;

    public MobileBannerApiController(MessageSource messageSource,
                                     RequestedContentTypeResolver requestedContentTypeResolver,
                                     GetAllBannersUseCase getAllBannersUseCase,
                                     GetBannersWithPaginationByTypeUseCase getBannersWithPaginationUseCase,
                                     GetBannersByTypeUseCase getBannersByTypeUseCase,
                                     RouteIsFavoriteUseCase routeIsFavoriteUseCase) {
        super(messageSource, requestedContentTypeResolver, routeIsFavoriteUseCase);
        this.getAllBannersUseCase = getAllBannersUseCase;
        this.getBannersWithPaginationUseCase = getBannersWithPaginationUseCase;
        this.getBannersByTypeUseCase = getBannersByTypeUseCase;
    }

    @Override
    protected String getControllerName() {
        return MobileBannerApiController.class.getSimpleName();
    }


    @GetMapping
    public Mono<ResponseEntity<ApiResponse<BannerList>>> getAllBanners() {
        return ok(Mono.just(true).as(getAllBannersUseCase::execute));
    }

    @GetMapping("/paginated")
    @Operation(
        summary = "Get paginated banners by type",
        description = "BREAKING CHANGE: page parameter is now 1-indexed (previously was 0-indexed). " +
                     "Use page=1 for first page."
    )
    public Mono<ResponseEntity<ApiResponse<BannerList>>> getBannersPaginated(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "displayOrder") String sortField,
            @RequestParam(defaultValue = "asc") String sortOrder,
            @RequestParam(defaultValue = "main") String type
    ) {

        validatePagination(page, size);

        BannerPaginationQuery query = BannerPaginationQuery.createWithType(
                page,
                size,
                camelToSnake(sortField),
                sortOrder,
                true,
                type
        );

        return okPaginated(Mono.just(query).as(getBannersWithPaginationUseCase::execute));
    }

    @GetMapping("/type/{type}")
    public Mono<ResponseEntity<ApiResponse<BannerList>>> getBannersByType(@PathVariable String type) {
        return ok(getBannersByTypeUseCase.execute(Mono.just(type)));
    }
}
