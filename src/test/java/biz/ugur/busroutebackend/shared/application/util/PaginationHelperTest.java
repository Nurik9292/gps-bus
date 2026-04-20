package biz.ugur.busroutebackend.shared.application.util;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaginationHelperTest {

    @Test
    void createPageableConvertsOneBasedToZeroBased() {
        Pageable pageable = PaginationHelper.createPageable(2, 20, "name", "asc");
        assertEquals(1, pageable.getPageNumber());
        assertEquals(20, pageable.getPageSize());
    }

    @Test
    void createPageableAscDefault() {
        Pageable pageable = PaginationHelper.createPageable(1, 10, "id", "asc");
        Sort.Order order = pageable.getSort().getOrderFor("id");
        assertEquals(Sort.Direction.ASC, order.getDirection());
    }

    @Test
    void createPageableDescWhenRequested() {
        Pageable pageable = PaginationHelper.createPageable(1, 10, "createdAt", "desc");
        assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("createdAt").getDirection());
    }

    @Test
    void createPageableDefaultsToAscForUnknownOrder() {
        Pageable pageable = PaginationHelper.createPageable(1, 10, "id", "bogus");
        assertEquals(Sort.Direction.ASC, pageable.getSort().getOrderFor("id").getDirection());
    }

    @Test
    void createPageableSortOverloadUsesGivenSort() {
        Sort sort = Sort.by(Sort.Direction.DESC, "updatedAt");
        Pageable pageable = PaginationHelper.createPageable(3, 5, sort);
        assertEquals(2, pageable.getPageNumber());
        assertEquals(sort, pageable.getSort());
    }
}
