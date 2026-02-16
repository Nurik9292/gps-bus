package biz.ugur.busroutebackend.suggestion.application.usecase;

import biz.ugur.busroutebackend.suggestion.application.dto.GetClientSuggestionsInput;
import biz.ugur.busroutebackend.suggestion.application.dto.SuggestionList;
import biz.ugur.busroutebackend.suggestion.application.dto.SuggestionResult;
import biz.ugur.busroutebackend.suggestion.domain.repository.AliasSuggestionRepository;
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
public class GetClientSuggestionsUseCase extends BaseUseCase<Mono<GetClientSuggestionsInput>, SuggestionList> {

    private final AliasSuggestionRepository suggestionRepository;

    public GetClientSuggestionsUseCase(AliasSuggestionRepository suggestionRepository,
                                        CorrelationContextService correlationService,
                                        EventBus eventBus) {
        super(correlationService, eventBus);
        this.suggestionRepository = suggestionRepository;
    }

    @Override
    protected Mono<SuggestionList> process(Mono<GetClientSuggestionsInput> request) {
        return request.flatMap(this::processInternal);
    }

    private Mono<SuggestionList> processInternal(GetClientSuggestionsInput input) {
        Pageable pageable = createPageable(input);

        return suggestionRepository.findByClientId(input.getClientId(), pageable)
                .map(SuggestionResult::fromDomain)
                .collectList()
                .zipWith(suggestionRepository.countByClientId(input.getClientId()))
                .map(tuple -> {
                    var items = tuple.getT1();
                    long total = tuple.getT2();
                    return new SuggestionList(items, total, input.getPage(), input.getSize(), total);
                });
    }

    private Pageable createPageable(GetClientSuggestionsInput input) {
        Sort.Direction direction = "desc".equalsIgnoreCase(input.getOrder())
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, input.getSort() != null ? input.getSort() : "created_at");
        return PageRequest.of(input.getPage() - 1, input.getSize(), sort);
    }

    @Override
    protected String getBoundContext() {
        return "suggestion";
    }
}
