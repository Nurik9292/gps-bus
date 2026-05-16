package biz.ugur.busroutebackend.advertising.application.usecase.mobile;

import biz.ugur.busroutebackend.advertising.domain.enums.PlacementKind;
import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.banner.application.dto.BannerList;
import biz.ugur.busroutebackend.banner.application.dto.BannerPaginationQuery;
import biz.ugur.busroutebackend.banner.application.dto.BannerResponse;
import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.banner.domain.exceptions.BannerValidationException;
import biz.ugur.busroutebackend.interfaces.rest.mobile.V1.mapper.BannerJsonMapper;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.shared.utility.BannerTypeTargetTypeMapper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class GetActiveBannersAsAdPlacementsUseCase
        extends BaseUseCase<TargetType, BannerList> {

    private static final List<TargetType> EDITORIAL_TARGETS = List.of(
            TargetType.HOME,
            TargetType.STOPS_LIST,
            TargetType.ROUTES_LIST,
            TargetType.PLACES_LIST,
            TargetType.POPUP);

    private final AdPlacementRepository placementRepository;

    public GetActiveBannersAsAdPlacementsUseCase(AdPlacementRepository placementRepository,
                                                 CorrelationContextService correlationService,
                                                 EventBus eventBus) {
        super(correlationService, eventBus);
        this.placementRepository = placementRepository;
    }

    public Mono<BannerList> executeAll() {
        return correlationService.executeWithCorrelation(
                Mono.defer(this::processAll),
                getBoundContext());
    }

    public Mono<BannerList> executePaginated(BannerPaginationQuery query) {
        return correlationService.executeWithCorrelation(
                Mono.defer(() -> processPaginated(query)),
                getBoundContext());
    }

    @Override
    protected Mono<BannerList> process(TargetType targetType) {
        return placementRepository
                .findActiveByKindAndTargetType(PlacementKind.EDITORIAL, targetType)
                .map(p -> BannerJsonMapper.fromAdPlacement(p, targetType))
                .collectList()
                .map(GetActiveBannersAsAdPlacementsUseCase::buildSinglePageList);
    }

    private Mono<BannerList> processAll() {
        return Flux.fromIterable(EDITORIAL_TARGETS)
                .concatMap(this::loadForTarget)
                .collectList()
                .map(GetActiveBannersAsAdPlacementsUseCase::buildSinglePageList);
    }

    private static BannerList buildSinglePageList(List<BannerResponse> items) {
        long total = items.size();
        int pageSize = Math.max(items.size(), 1);
        return BannerList.of(items, total, 1, pageSize, total);
    }

    private Mono<BannerList> processPaginated(BannerPaginationQuery query) {
        TargetType targetType = resolveTargetType(query.getType());
        int page = query.getPage();
        int size = query.getSize();
        return placementRepository
                .findActiveByKindAndTargetType(PlacementKind.EDITORIAL, targetType)
                .map(p -> BannerJsonMapper.fromAdPlacement(p, targetType))
                .collectList()
                .map(all -> {
                    long total = all.size();
                    int from = Math.min((page - 1) * size, all.size());
                    int to = Math.min(from + size, all.size());
                    List<BannerResponse> slice = all.subList(from, to);
                    return BannerList.of(slice, total, page, size, total);
                });
    }

    private Flux<BannerResponse> loadForTarget(TargetType targetType) {
        return placementRepository
                .findActiveByKindAndTargetType(PlacementKind.EDITORIAL, targetType)
                .map(p -> BannerJsonMapper.fromAdPlacement(p, targetType));
    }

    private TargetType resolveTargetType(String rawType) {
        BannerType bannerType = BannerType.fromValue(rawType);
        if (bannerType == BannerType.STOP_BUTTON) {
            throw new BannerValidationException(
                    "Banner type 'stop-button' is no longer supported");
        }
        return BannerTypeTargetTypeMapper.toTarget(bannerType);
    }

    @Override
    protected String getBoundContext() {
        return "advertising.mobile";
    }
}
