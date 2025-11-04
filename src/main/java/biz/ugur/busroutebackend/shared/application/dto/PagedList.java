package biz.ugur.busroutebackend.shared.application.dto;

import java.util.List;

public interface PagedList<T> {

    List<T> items();

    Long activeCount();

    PaginationInfo pagination();

    default int size() {
        return items().size();
    }

    default boolean isEmpty() {
        return items().isEmpty();
    }

    default boolean hasContent() {
        return !items().isEmpty();
    }

    default boolean hasNext() {
        return pagination().hasNext();
    }

    default boolean hasPrevious() {
        return pagination().hasPrevious();
    }

    default boolean isFirst() {
        return pagination().isFirst();
    }

    default boolean isLast() {
        return pagination().isLast();
    }
}
