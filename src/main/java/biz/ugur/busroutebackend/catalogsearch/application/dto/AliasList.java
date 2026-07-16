package biz.ugur.busroutebackend.catalogsearch.application.dto;

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
public final class AliasList implements PagedList<AliasResult> {

    private final List<AliasResult> aliases;
    private final Long activeCount;
    private final PaginationInfo pagination;

    public AliasList(List<AliasResult> aliases, int currentPage, int pageSize, long totalItems) {
        this.aliases = Collections.unmodifiableList(aliases);
        this.activeCount = totalItems;
        this.pagination = PaginationInfo.of(currentPage, pageSize, totalItems);
    }

    @Override
    public List<AliasResult> items() {
        return aliases;
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
