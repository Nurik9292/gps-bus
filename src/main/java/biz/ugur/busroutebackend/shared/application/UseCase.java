package biz.ugur.busroutebackend.shared.application;

/**
 * @deprecated Use {@link ReactiveUseCase} (Mono) or {@link ReactiveFluxUseCase} (Flux)
 * for new code. Kept for binary compatibility with existing implementations.
 */
@Deprecated(since = "2026-04-17", forRemoval = false)
public interface UseCase<REQUEST, RESPONSE> {

    RESPONSE execute(REQUEST request);
}