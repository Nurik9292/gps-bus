package biz.ugur.busroutebackend.complaint.application.dto;

import biz.ugur.busroutebackend.shared.application.dto.PagedList;
import biz.ugur.busroutebackend.shared.application.dto.PaginationInfo;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.List;

@ToString
@Getter
@EqualsAndHashCode
public final class ComplaintList implements PagedList<ComplaintResult> {

    private final List<ComplaintResult> complaints;
    private final Long activeCount;
    private final PaginationInfo pagination;

    public ComplaintList(List<ComplaintResult> complaints, Long activeCount, int currentPage, int pageSize, long totalItems) {
        this.complaints = Collections.unmodifiableList(complaints);
        this.activeCount = activeCount;
        this.pagination = PaginationInfo.of(currentPage, pageSize, totalItems);
    }

    @Override
    public List<ComplaintResult> items() {
        return complaints;
    }

    @Override
    public Long activeCount() {
        return activeCount;
    }

    @Override
    public PaginationInfo pagination() {
        return pagination;
    }
}
