package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.controller;

import biz.ugur.busroutebackend.client.application.usecase.RouteIsFavoriteUseCase;
import biz.ugur.busroutebackend.notification.application.dto.NotificationList;
import biz.ugur.busroutebackend.notification.application.dto.NotificationPaginationQuery;
import biz.ugur.busroutebackend.notification.application.usecase.admin.GetAllNotificationsUseCase;
import biz.ugur.busroutebackend.notification.application.usecase.client.GetNotificationsWithPaginationUseCase;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.accept.RequestedContentTypeResolver;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_MOBILE_NOTIFICATIONS;

@RestController
@RequestMapping(V1_MOBILE_NOTIFICATIONS)
public class MobileNotificationApiController extends BaseMobileController {

    private final GetAllNotificationsUseCase getAllNotificationsUseCase;
    private final GetNotificationsWithPaginationUseCase getNotificationsWithPaginationUseCase;

    public MobileNotificationApiController(MessageSource messageSource,
                                           RequestedContentTypeResolver requestedContentTypeResolver,
                                           GetAllNotificationsUseCase getAllNotificationsUseCase,
                                           GetNotificationsWithPaginationUseCase getNotificationsWithPaginationUseCase,
                                           RouteIsFavoriteUseCase routeIsFavoriteUseCase) {
        super(messageSource, requestedContentTypeResolver, routeIsFavoriteUseCase);
        this.getAllNotificationsUseCase = getAllNotificationsUseCase;
        this.getNotificationsWithPaginationUseCase = getNotificationsWithPaginationUseCase;
    }

    @Override
    protected String getControllerName() {
        return MobileNotificationApiController.class.getSimpleName();
    }


    @GetMapping
    public Mono<ResponseEntity<ApiResponse<NotificationList>>> getAllNotifications() {
        return ok(Mono.just(true).as(getAllNotificationsUseCase::execute));
    }

    @GetMapping("/paginated")
    @Operation(
        summary = "Get paginated notifications",
        description = "Page parameter is 1-indexed. Use page=1 for first page."
    )
    public Mono<ResponseEntity<ApiResponse<NotificationList>>> getNotificationsPaginated(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "displayOrder") String sortField,
            @RequestParam(defaultValue = "asc") String sortOrder
    ) {

        validatePagination(page, size);

        NotificationPaginationQuery query = NotificationPaginationQuery.create(
                page,
                size,
                camelToSnake(sortField),
                sortOrder,
                true,
                null
        );

        return okPaginated(Mono.just(query).as(getNotificationsWithPaginationUseCase::execute));
    }
}
