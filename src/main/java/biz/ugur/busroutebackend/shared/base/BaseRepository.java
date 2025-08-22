package biz.ugur.busroutebackend.shared.base;

import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BaseRepository<T, ID> {

    Mono<T> save(T entity);

    Flux<T> findAll();

    Flux<T> findAll(Pageable pageable);

    Mono<T> findById(ID id);

    Mono<Boolean> existsById(ID id);

    Mono<Void> deleteById(ID id);

    Mono<Long> count();
}
