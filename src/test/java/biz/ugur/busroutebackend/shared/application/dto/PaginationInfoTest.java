package biz.ugur.busroutebackend.shared.application.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaginationInfoTest {

    @Test
    void computesTotalPagesCeiling() {
        PaginationInfo info = PaginationInfo.of(1, 10, 25);
        assertEquals(3, info.getTotalPages());
    }

    @Test
    void totalPagesIsZeroWhenTotalItemsZero() {
        PaginationInfo info = PaginationInfo.of(1, 10, 0);
        assertEquals(0, info.getTotalPages());
    }

    @Test
    void hasNextReturnsTrueWhenNotOnLastPage() {
        PaginationInfo info = PaginationInfo.of(1, 10, 25);
        assertTrue(info.hasNext());
    }

    @Test
    void hasPreviousReturnsTrueWhenNotOnFirstPage() {
        PaginationInfo info = PaginationInfo.of(2, 10, 25);
        assertTrue(info.hasPrevious());
    }

    @Test
    void isFirstAndIsLastFlags() {
        PaginationInfo first = PaginationInfo.of(1, 10, 25);
        PaginationInfo last = PaginationInfo.of(3, 10, 25);

        assertTrue(first.isFirst());
        assertFalse(first.isLast());
        assertTrue(last.isLast());
        assertFalse(last.isFirst());
    }

    @Test
    void emptyPageIsAlsoLast() {
        PaginationInfo info = PaginationInfo.of(1, 10, 0);
        assertTrue(info.isLast());
    }

    @Test
    void rejectsCurrentPageLessThanOne() {
        assertThrows(IllegalArgumentException.class, () -> PaginationInfo.of(0, 10, 0));
    }

    @Test
    void rejectsPageSizeLessThanOne() {
        assertThrows(IllegalArgumentException.class, () -> PaginationInfo.of(1, 0, 0));
    }

    @Test
    void rejectsNegativeTotalItems() {
        assertThrows(IllegalArgumentException.class, () -> PaginationInfo.of(1, 10, -1));
    }
}
