package biz.ugur.busroutebackend.subscription.application.dto;

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
public final class SubscriptionList implements PagedList<SubscriptionResponse> {

    private final List<SubscriptionResponse> subscriptions;
    private final Long activeCount;
    private final PaginationInfo pagination;

    public SubscriptionList(List<SubscriptionResponse> subscriptions, Long activeCount,
                            int currentPage, int pageSize, long totalItems) {
        this.subscriptions = Collections.unmodifiableList(subscriptions);
        this.activeCount = activeCount;
        this.pagination = PaginationInfo.of(currentPage, pageSize, totalItems);
    }

    public static SubscriptionList of(List<SubscriptionResponse> subscriptions, Long activeCount,
                                      int currentPage, int pageSize, long totalItems) {
        return new SubscriptionList(subscriptions, activeCount, currentPage, pageSize, totalItems);
    }

    @Override public List<SubscriptionResponse> items() { return subscriptions; }
    @Override public Long activeCount() { return activeCount; }
    @Override public PaginationInfo pagination() { return pagination; }
}
