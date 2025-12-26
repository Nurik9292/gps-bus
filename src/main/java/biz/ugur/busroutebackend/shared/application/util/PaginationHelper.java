package biz.ugur.busroutebackend.shared.application.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PaginationHelper {

    private PaginationHelper() {
    }

    public static Pageable createPageable(int page, int size, String sortField, String sortOrder) {
        Sort.Direction direction = "desc".equalsIgnoreCase(sortOrder)
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, sortField);
        return PageRequest.of(page - 1, size, sort);
    }

    public static Pageable createPageable(int page, int size, Sort sort) {
        return PageRequest.of(page - 1, size, sort);
    }
}
