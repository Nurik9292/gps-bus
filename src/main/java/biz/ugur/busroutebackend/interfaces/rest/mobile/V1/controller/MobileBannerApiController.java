package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.controller;

import biz.ugur.busroutebackend.banner.appication.dto.BannerListResponse;
import biz.ugur.busroutebackend.banner.appication.dto.BannerPaginationQuery;
import biz.ugur.busroutebackend.banner.appication.usecase.admin.GetAllBannersUseCase;
import biz.ugur.busroutebackend.banner.appication.usecase.admin.GetBannersByTypeUseCase;
import biz.ugur.busroutebackend.banner.appication.usecase.client.GetBannersWithPaginationByTypeUseCase;
import biz.ugur.busroutebackend.client.application.usecase.RouteIsFavoriteUseCase;
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
    public Mono<ResponseEntity<ApiResponse<BannerListResponse>>> getAllBanners() {
        return ok(Mono.just(true).as(getAllBannersUseCase::execute));

    }

    @GetMapping("/paginated")
    public Mono<ResponseEntity<ApiResponse<BannerListResponse>>> getBannersPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "displayOrder") String sortField,
            @RequestParam(defaultValue = "asc") String sortOrder,
            @RequestParam(defaultValue = "main") String type
    ) {


        BannerPaginationQuery query =  BannerPaginationQuery.createWithType(
                page,
                size,
                sortField,
                sortOrder,
                true,
                type
        );

        return ok(Mono.just(query).as(getBannersWithPaginationUseCase::execute));
    }

    @GetMapping("/type/{type}")
    public Mono<ResponseEntity<ApiResponse<BannerListResponse>>> getBannersByType(@PathVariable String type) {
        return ok(getBannersByTypeUseCase.execute(Mono.just(type)));
    }
}
