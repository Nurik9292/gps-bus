package biz.ugur.busroutebackend.suggestion.application.usecase;

import biz.ugur.busroutebackend.suggestion.application.dto.ReviewSuggestionInput;
import biz.ugur.busroutebackend.suggestion.application.dto.SuggestionResult;
import biz.ugur.busroutebackend.suggestion.domain.exceptions.SuggestionNotFoundException;
import biz.ugur.busroutebackend.suggestion.domain.model.AliasSuggestion;
import biz.ugur.busroutebackend.suggestion.domain.repository.AliasSuggestionRepository;
import biz.ugur.busroutebackend.suggestion.domain.valueobjects.AliasSuggestionId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class RejectSuggestionUseCase extends BaseUseCase<Mono<ReviewSuggestionInput>, SuggestionResult> {

    private final AliasSuggestionRepository suggestionRepository;

    public RejectSuggestionUseCase(AliasSuggestionRepository suggestionRepository,
                                    CorrelationContextService correlationService,
                                    EventBus eventBus) {
        super(correlationService, eventBus);
        this.suggestionRepository = suggestionRepository;
    }

    @Override
    protected Mono<SuggestionResult> process(Mono<ReviewSuggestionInput> request) {
        return request.flatMap(cmd ->
                suggestionRepository.findById(AliasSuggestionId.of(cmd.suggestionId()))
                        .switchIfEmpty(Mono.error(new SuggestionNotFoundException(cmd.suggestionId())))
                        .flatMap(suggestion -> {
                            AliasSuggestion rejected = suggestion.reject(cmd.reviewerId(), cmd.comment());
                            return suggestionRepository.save(rejected)
                                    .map(SuggestionResult::fromDomain);
                        })
        );
    }

    @Override
    protected String getBoundContext() {
        return "suggestion";
    }
}
