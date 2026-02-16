package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.GetAllPlacesInput;
import biz.ugur.busroutebackend.place.application.dto.PlaceList;
import biz.ugur.busroutebackend.place.application.dto.PlaceResult;
import biz.ugur.busroutebackend.place.domain.repository.PlaceRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GetAllPlacesUseCase extends BaseUseCase<Mono<GetAllPlacesInput>, PlaceList> {

    private final PlaceRepository placeRepository;

    public GetAllPlacesUseCase(PlaceRepository placeRepository,
                               CorrelationContextService correlationService,
                               EventBus eventBus) {
        super(correlationService, eventBus);
        this.placeRepository = placeRepository;
    }

    @Override
    protected Mono<PlaceList> process(Mono<GetAllPlacesInput> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "place";
    }

    private Mono<PlaceList> processInternal(GetAllPlacesInput input) {
        Pageable pageable = createPageable(input);

        return placeRepository.findByFilters(input.getCityId(), input.getCategory(), input.getSearch(), pageable)
                .map(PlaceResult::fromDomain)
                .collectList()
                .zipWith(placeRepository.countByFilters(input.getCityId(), input.getCategory(), input.getSearch()))
                .zipWith(placeRepository.count())
                .map(tuple -> {
                    var items = tuple.getT1().getT1();
                    long totalFiltered = tuple.getT1().getT2();
                    long totalCount = tuple.getT2();
                    return new PlaceList(items, totalCount, input.getPage(), input.getSize(), totalFiltered);
                });
    }

    private Pageable createPageable(GetAllPlacesInput input) {
        Sort.Direction direction = "desc".equalsIgnoreCase(input.getOrder())
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, input.getSort() != null ? input.getSort() : "name");
        return PageRequest.of(input.getPage() - 1, input.getSize(), sort);
    }
}
