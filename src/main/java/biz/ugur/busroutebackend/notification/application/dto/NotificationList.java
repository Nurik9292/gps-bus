package biz.ugur.busroutebackend.notification.application.dto;

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
public final class NotificationList implements PagedList<NotificationResponse> {

    private final List<NotificationResponse> notifications;
    private final Long activeCount;
    private final PaginationInfo pagination;

    public NotificationList(List<NotificationResponse> notifications, Long activeCount, int currentPage, int pageSize, long totalItems) {
        this.notifications = Collections.unmodifiableList(notifications);
        this.activeCount = activeCount;
        this.pagination = PaginationInfo.of(currentPage, pageSize, totalItems);
    }

    public static NotificationList of(
        List<NotificationResponse> notifications,
        Long activeCount,
        int currentPage,
        int pageSize,
        long totalItems
    ) {
        return new NotificationList(notifications, activeCount, currentPage, pageSize, totalItems);
    }


    @Override
    public List<NotificationResponse> items() {
        return notifications;
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
