package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.StreetList;
import biz.ugur.busroutebackend.place.application.dto.StreetResult;
import biz.ugur.busroutebackend.place.domain.repository.StreetRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class GetAllStreetsUseCase extends BaseUseCase<Mono<GetAllStreetsUseCase.Input>, StreetList> {

    private final StreetRepository streetRepository;

    public GetAllStreetsUseCase(StreetRepository streetRepository,
                                CorrelationContextService correlationService,
                                EventBus eventBus) {
        super(correlationService, eventBus);
        this.streetRepository = streetRepository;
    }

    @Override
    protected Mono<StreetList> process(Mono<Input> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "place";
    }

    private Mono<StreetList> processInternal(Input input) {
        Pageable pageable = PageRequest.of(
                input.page - 1, input.size,
                Sort.by("desc".equalsIgnoreCase(input.order) ? Sort.Direction.DESC : Sort.Direction.ASC,
                        input.sort != null ? input.sort : "name")
        );

        if (input.cityId != null) {
            return streetRepository.findByCityId(input.cityId, pageable)
                    .map(StreetResult::fromDomain)
                    .collectList()
                    .zipWith(streetRepository.countByCityId(input.cityId))
                    .zipWith(streetRepository.count())
                    .map(tuple -> new StreetList(
                            tuple.getT1().getT1(),
                            tuple.getT2(),
                            input.page, input.size,
                            tuple.getT1().getT2()
                    ));
        }

        return streetRepository.findAll(pageable)
                .map(StreetResult::fromDomain)
                .collectList()
                .zipWith(streetRepository.count())
                .map(tuple -> new StreetList(
                        tuple.getT1(),
                        tuple.getT2(),
                        input.page, input.size,
                        tuple.getT2()
                ));
    }

    public record Input(int page, int size, String sort, String order, String cityId) {
        public static Input fromParams(int page, int size, String sort, String order, String cityId) {
            return new Input(page, size, sort, order, cityId);
        }
    }
}
