package biz.ugur.busroutebackend.shared.application;

import biz.ugur.busroutebackend.shared.domain.CorrelationId;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class CorrelationContextService {

    private static final String CORRELATION_ID_KEY = "correlationId";

    public <T> Mono<T> withCorrelationId(Mono<T> mono, CorrelationId correlationId) {
        return mono.contextWrite(Context.of(CORRELATION_ID_KEY, correlationId));
    }

    public Mono<CorrelationId> getCurrentCorrelationId() {
        return Mono.deferContextual(contextView -> {
            if (contextView.hasKey(CORRELATION_ID_KEY)) {
                return Mono.just(contextView.get(CORRELATION_ID_KEY));
            }
            return Mono.just(CorrelationId.generate());
        });
    }

    public <T> Mono<T> executeWithCorrelation(Mono<T> operation, String boundedContext) {
        CorrelationId correlationId = switch (boundedContext.toLowerCase()) {
            case "admin" -> CorrelationId.forAdmin();
            case "transport" -> CorrelationId.forTransport();
            case "routing" -> CorrelationId.forRouting();
            default -> CorrelationId.generate();
        };

        return withCorrelationId(operation, correlationId);
    }
}
